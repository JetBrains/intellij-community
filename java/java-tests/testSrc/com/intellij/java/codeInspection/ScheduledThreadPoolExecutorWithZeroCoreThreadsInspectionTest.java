// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ScheduledThreadPoolExecutorWithZeroCoreThreadsInspection;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ScheduledThreadPoolExecutorWithZeroCoreThreadsInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/scheduledThreadPoolExecutorWithZeroCoreThreads";
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new ScheduledThreadPoolExecutorWithZeroCoreThreadsInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
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

  public void testSimple() {
    doTest();
  }
}