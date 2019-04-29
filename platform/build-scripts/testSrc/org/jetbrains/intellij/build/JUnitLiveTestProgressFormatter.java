// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.optional.junit.IgnoredTestListener;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * @author nik
 */
@SuppressWarnings("unused") //used in org.jetbrains.intellij.build.impl.TestingTasksImpl
public class JUnitLiveTestProgressFormatter implements JUnitResultFormatter, IgnoredTestListener {
  private PrintWriter myOut;

  @Override
  public void setOutput(OutputStream out) {
    myOut = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
  }

  @Override
  public void startTest(Test test) {
    printMessage(test, null);
  }

  @Override
  public void endTest(Test test) {
    printMessage(test, "OK");
  }

  @Override
  public void testIgnored(Test test) {
    printMessage(test, "ignored");
  }

  @Override
  public void testAssumptionFailure(Test test, Throwable exception) {
    printMessage(test, "assumption violated (" + exception.toString() + ")");
  }

  @Override
  public void addError(Test test, Throwable e) {
    printMessage(test, "error (" + e.toString() + ")");
  }

  @Override
  public void addFailure(Test test, AssertionFailedError e) {
    printMessage(test, "failed (" + e.toString() + ")");
  }

  @Override
  public void startTestSuite(JUnitTest suite) throws BuildException {
  }

  @Override
  public void endTestSuite(JUnitTest suite) throws BuildException {

  }

  @Override
  public void setSystemOutput(String out) {
  }

  @Override
  public void setSystemError(String err) {
  }

  private void printMessage(Test test, String message) {
    if (myOut != null) {
      myOut.println(getTestName(test) + (message != null ? ": " + message : ""));
      myOut.flush();
    }
  }

  private static String getTestName(Test test) {
    if (test instanceof TestCase) {
      return test.getClass().getName() + "." + ((TestCase)test).getName();
    }
    return String.valueOf(test);
  }
}
