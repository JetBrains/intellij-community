/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution;

import java.lang.reflect.Method;

public abstract class TestDiscoveryListener {
  public abstract String getFrameworkId();
  public void testStarted(String className, String methodName) {
    try {
      final Object data = getData();
      Method testStarted = data.getClass().getMethod("testDiscoveryStarted", new Class[] {String.class});
      testStarted.invoke(data, new Object[] {className + "-" + methodName});
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public void testFinished(String className, String methodName, boolean succeed) {
    if (succeed) {
      try {
        final Object data = getData();
        Method testEnded = data.getClass().getMethod("testDiscoveryEnded", new Class[] {String.class});
        testEnded.invoke(data, new Object[] {getFrameworkId() + className + "-" + methodName});
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
  }

  protected Object getData() throws Exception {
    return Class.forName("com.intellij.rt.coverage.data.ProjectData")
        .getMethod("getProjectData", new Class[0])
        .invoke(null, new Object[0]);
  }

  public void testRunStarted(String name) {}

  public void testRunFinished(String name) {}
}
