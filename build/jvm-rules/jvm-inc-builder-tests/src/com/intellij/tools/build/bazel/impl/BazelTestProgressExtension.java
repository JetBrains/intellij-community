package com.intellij.tools.build.bazel.impl;

import org.junit.jupiter.api.extension.*;

public class BazelTestProgressExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, TestWatcher {
  private int myTotalTests = 0;
  private int myPassedTests = 0;
  private int myFailedTests = 0;
  private long mySuiteStartTime;
  private long myTestStartTime;

  @Override
  public void beforeAll(ExtensionContext context) {
    mySuiteStartTime = System.currentTimeMillis();
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    myTestStartTime = System.currentTimeMillis();
    myTotalTests++;
    System.out.println("[ RUN      ] " + getTestName(context));
    System.out.flush();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    // Handled by TestWatcher methods
  }

  @Override
  public void testSuccessful(ExtensionContext context) {
    long duration = System.currentTimeMillis() - myTestStartTime;
    myPassedTests++;
    System.out.println("[       OK ] " + getTestName(context) + " (" + duration + " ms)");
    System.out.flush();
  }

  @Override
  public void testFailed(ExtensionContext context, Throwable cause) {
    long duration = System.currentTimeMillis() - myTestStartTime;
    myFailedTests++;
    System.out.println("[  FAILED  ] " + getTestName(context) + " (" + duration + " ms)");
    System.out.flush();
  }

  @Override
  public void testAborted(ExtensionContext context, Throwable cause) {
    long duration = System.currentTimeMillis() - myTestStartTime;
    System.out.println("[  SKIPPED ] " + getTestName(context) + " (" + duration + " ms)");
    System.out.flush();
  }

  @Override
  public void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
    System.out.println("[ DISABLED ] " + getTestName(context));
    System.out.flush();
  }

  @Override
  public void afterAll(ExtensionContext context) {
    long totalDuration = System.currentTimeMillis() - mySuiteStartTime;
    String suiteName = context.getDisplayName();

    System.out.println("[----------] " + myTotalTests + " tests from " + suiteName + " (" + totalDuration + " ms total)");
    System.out.println("[==========] " + myTotalTests + " tests ran. (" + totalDuration + " ms total)");
    System.out.println("[  PASSED  ] " + myPassedTests + " tests.");
    if (myFailedTests > 0) {
      System.out.println("[  FAILED  ] " + myFailedTests + " tests.");
    }
    System.out.flush();
  }

  private static String getTestName(ExtensionContext context) {
    return context.getParent().map(ExtensionContext::getDisplayName).orElse("") + "." + context.getDisplayName();
  }
}