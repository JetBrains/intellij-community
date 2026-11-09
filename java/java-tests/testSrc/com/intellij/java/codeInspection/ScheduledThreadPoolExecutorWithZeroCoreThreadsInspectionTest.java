// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ScheduledThreadPoolExecutorWithZeroCoreThreadsInspection;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ScheduledThreadPoolExecutorWithZeroCoreThreadsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ScheduledThreadPoolExecutorWithZeroCoreThreadsInspection(), getEnvironmentClasses());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/scheduledThreadPoolExecutorWithZeroCoreThreads");
  }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      """
package java.util.concurrent;
public class ThreadPoolExecutor {
public void setCorePoolSize(int corePoolSize) {}
}""",

      """
package java.util.concurrent;
public class ScheduledThreadPoolExecutor
        extends ThreadPoolExecutor {
    public ScheduledThreadPoolExecutor(int corePoolSize) {}
    @Override
    public void setCorePoolSize(int corePoolSize) {}
}""",
    };
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest();
  }
}