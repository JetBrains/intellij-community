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

public class TestDiscoveryListener {
  public void testStarted(String className, String methodName) {
    final Object data = getData();
    try {
      Method testStarted = data.getClass().getMethod("testStarted", new Class[] {String.class});
      testStarted.invoke(data, new Object[] {className + "-" + methodName});
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public void testFinished(String className, String methodName) {
    final Object data = getData();
    try {
      Method testEnded = data.getClass().getMethod("testEnded", new Class[] {String.class});
      testEnded.invoke(data, new Object[] {className + "-" + methodName});
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  protected Object getData() {
    try {
      return Class.forName("org.jetbrains.testme.instrumentation.ProjectData")
        .getMethod("getProjectData", new Class[0])
        .invoke(null, new Object[0]);

    } catch (Exception e) {
      return null; //should not happen
    }
  }
}
