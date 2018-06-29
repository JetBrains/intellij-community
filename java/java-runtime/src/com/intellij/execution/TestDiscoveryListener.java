// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import java.lang.reflect.Method;

public abstract class TestDiscoveryListener {
  public abstract String getFrameworkId();
  public void testStarted(String className, String methodName) {
    try {
      final Object data = getData();
      Method testStarted = data.getClass().getMethod("testDiscoveryStarted", new Class[] {String.class, String.class});
      testStarted.invoke(data, new Object[] {className, methodName});
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public void testFinished(String className, String methodName, boolean succeed) {
    if (succeed) {
      try {
        final Object data = getData();
        Method testEnded = data.getClass().getMethod("testDiscoveryEnded", new Class[] {String.class, String.class});
        testEnded.invoke(data, new Object[] {className, methodName});
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
  }

  protected Object getData() throws Exception {
    return Class.forName("com.intellij.rt.coverage.data.TestDiscoveryProjectData")
        .getMethod("getProjectData", new Class[0])
        .invoke(null, new Object[0]);
  }

  public void testRunStarted(String name) {}

  public void testRunFinished(String name) {}
}
