package org.apache.maven.surefire.junitcore;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.surefire.report.ConsoleOutputReceiver;
import org.apache.maven.surefire.report.ConsoleOutputReceiverForCurrentThread;
import org.apache.maven.surefire.report.ReportEntry;
import org.apache.maven.surefire.report.Reporter;

/**
 * Represents the test-state of a single test method that is run.
 * <p/>
 * Notes about thread safety: This instance is serially confined to 1-3 threads (construction, test-run, reporting),
 * without any actual parallel access
 */
class TestMethod
    implements ConsoleOutputReceiver
{
    private final ReportEntry description;

    private final long startTime;

    private long endTime;

    private volatile ReportEntry testFailure;

    private volatile ReportEntry testError;

    private volatile ReportEntry ignored;

    private static final InheritableThreadLocal<TestMethod> TEST_METHOD = new InheritableThreadLocal<TestMethod>();

    private volatile LogicalStream output;

    public TestMethod( ReportEntry description )
    {
        this.description = description;
        startTime = System.currentTimeMillis();
    }

    public void testFinished()
    {
        setEndTime();
    }

    public void testIgnored( ReportEntry description )
    {
        ignored = description;
        setEndTime();
    }

    public void testFailure( ReportEntry failure )
    {
        this.testFailure = failure;
    }

    public void testError( ReportEntry failure )
    {
        this.testError = failure;
        setEndTime();
    }

    private void setEndTime()
    {
        this.endTime = System.currentTimeMillis();
    }

    public int getElapsed()
    {
        return (int) ( endTime - startTime );
    }


    public void replay( Reporter reporter )
        throws Exception
    {

        if ( ignored != null )
        {
            reporter.testSkipped( createReportEntry() );
            return;
        }

        reporter.testStarting( createReportEntry() );
        if ( output != null )
        {
            output.writeDetails( reporter );
        }

        if ( testFailure != null )
        {
            reporter.testFailed( testFailure, getStdout(), getStdErr() );
        }
        else if ( testError != null )
        {
            reporter.testError( testError, getStdout(), getStdErr() );
        }
        else
        {
            reporter.testSucceeded( createReportEntry() );
        }
    }

    private ReportEntry createReportEntry()
    {
        return this.description;
    }

    public void attachToThread()
    {
        TEST_METHOD.set( this );
        ConsoleOutputReceiverForCurrentThread.set( this );

    }

    public static void detachFromCurrentThread()
    {
        TEST_METHOD.remove();
        ConsoleOutputReceiverForCurrentThread.remove();
    }

    public static TestMethod getThreadTestMethod()
    {
        return TEST_METHOD.get();
    }

    public LogicalStream getLogicalStream()
    {
        if ( output == null )
        {
            output = new LogicalStream();
        }
        return output;
    }

    public void writeTestOutput( byte[] buf, int off, int len, boolean stdout )
    {
        getLogicalStream().write( stdout, buf, off, len );
    }

    private String getStdout()
    {
        return output != null ? output.getOutput( true ) : "";
    }

    private String getStdErr()
    {
        return output != null ? output.getOutput( false ) : "";
    }
}
