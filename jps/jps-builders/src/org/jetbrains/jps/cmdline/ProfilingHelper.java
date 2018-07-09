/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.cmdline;

import java.lang.reflect.Method;

/**
 * @author Eugene Zhuravlev
 */
class ProfilingHelper {

  private final Class<?> myControllerClass;
  private final Object myController;

  ProfilingHelper() throws Exception {
    myControllerClass = Class.forName("com.yourkit.api.Controller");
    myController = myControllerClass.newInstance();
  }

  public void startProfiling() {
    try {
      final Method startMethod = myControllerClass.getDeclaredMethod("startCPUSampling", String.class);
      if (startMethod != null) {
        startMethod.invoke(myController, new Object[] {null});
      }
      else {
        System.err.println("Cannot find method 'startCPUProfiling' in class " + myControllerClass.getName());
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }

  public void stopProfiling() {
    try {
      final Method captureMethod = myControllerClass.getDeclaredMethod("captureSnapshot", long.class);
      if (captureMethod != null) {
        final String path = (String)captureMethod.invoke(myController, 0L/*ProfilingModes.SNAPSHOT_WITHOUT_HEAP*/);
        System.err.println("CPU Snapshot captured: " + path);
        final Method stopMethod = myControllerClass.getDeclaredMethod("stopCPUProfiling");
        if (stopMethod != null) {
          stopMethod.invoke(myController);
        }
        else {
          System.err.println("Cannot find method 'stopCPUProfiling' in class " + myControllerClass.getName());
        }
      }
      else {
        System.err.println("Cannot find method 'captureSnapshot' in class " + myControllerClass.getName());
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }
}
