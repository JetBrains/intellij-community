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

import com.intellij.openapi.diagnostic.Logger;

@SuppressWarnings("ALL")
class ProfilingHelper {
  private static final Logger LOG = Logger.getInstance(ProfilingHelper.class);

  private final Class<?> myControllerClass;
  private final Object myController;

  ProfilingHelper() throws Exception {
    myControllerClass = Class.forName("com.yourkit.api.controller.Controller");
    //     final Controller controller = Controller.newBuilder().self().build();
    final Object builder = myControllerClass.getDeclaredMethod("newBuilder").invoke(null);
    final Class<?> builderClass = builder.getClass();
    myController = builderClass.getMethod("build").invoke(builderClass.getMethod("self").invoke(builder));
  }

  private void startProfiling(String startMethod) throws Exception {
    final Class<?> settingsClass = Class.forName("com.yourkit.api.controller.CpuProfilingSettings");
    myControllerClass.getMethod(startMethod, settingsClass).invoke(myController, new Object[]{settingsClass.newInstance()});
  }

  public void startSamplingProfiling() throws Exception{
    startProfiling("startSampling");
  }

  public void startTracingProfiling() throws Exception{
    startProfiling("startTracing");
  }

  public void stopProfiling() {
    try {
      final String path = (String)myControllerClass.getMethod("capturePerformanceSnapshot").invoke(myController);

      String message = "CPU Snapshot captured: " + path;
      LOG.warn(message);
      System.err.println(message);

      myControllerClass.getMethod("stopCpuProfiling").invoke(myController);
    }
    catch (Throwable e) {
      e.printStackTrace();
      LOG.error(e);
    }
  }
}
