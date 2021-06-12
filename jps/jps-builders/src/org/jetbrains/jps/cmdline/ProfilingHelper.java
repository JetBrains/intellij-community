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

@SuppressWarnings("ALL")
class ProfilingHelper {
  private final Class<?> myControllerClass;
  private final Object myController;

  ProfilingHelper() throws Exception {
    myControllerClass = Class.forName("com.yourkit.api.Controller");
    myController = myControllerClass.newInstance();
  }

  public void startProfiling() {
    try {
      myControllerClass.getDeclaredMethod("startCPUSampling", String.class).invoke(myController, new Object[] {null});
    }
    catch (NoSuchMethodException e) {
      System.err.println("Cannot find method 'startCPUProfiling' in class " + myControllerClass.getName());
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }

  public void stopProfiling() {
    try {
      final String path = (String)myControllerClass.getDeclaredMethod("captureSnapshot", long.class).invoke(myController, 0L/*ProfilingModes.SNAPSHOT_WITHOUT_HEAP*/);
      System.err.println("CPU Snapshot captured: " + path);
      try {
        myControllerClass.getDeclaredMethod("stopCPUProfiling").invoke(myController);
      }
      catch (NoSuchMethodException e) {
        System.err.println("Cannot find method 'stopCPUProfiling' in class " + myControllerClass.getName());
      }
    }
    catch (NoSuchMethodException e) {
      System.err.println("Cannot find method 'captureSnapshot' in class " + myControllerClass.getName());
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }
}
