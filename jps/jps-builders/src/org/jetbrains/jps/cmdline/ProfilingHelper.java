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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("ALL")
class ProfilingHelper {
  private final Class<?> myControllerClass;
  private final Object myController;

  ProfilingHelper() throws Exception {
    Class<?> cls = null;
    final List<String> controllerClassNames = Arrays.asList("com.yourkit.api.Controller", "com.yourkit.api.controller.Controller");
    for (String name : controllerClassNames) {
      try {
        cls = Class.forName(name);
        break;
      }
      catch (ClassNotFoundException ignored) {
      }
    }

    if (cls == null) {
      final StringBuilder message = new StringBuilder();
      message.append("Classes ");
      for (Object name : controllerClassNames) {
        message.append(name).append(" ");
      }
      message.append("not found");
      throw new ClassNotFoundException(message.toString());
    }
    
    myControllerClass = cls;
    myController = myControllerClass.newInstance();
  }

  public void startProfiling() {
    try {
      getAPIMethod(Arrays.asList("startSampling", "startCPUSampling"), String.class).invoke(myController, new Object[] {null});
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
      final String path = (String)getAPIMethod("captureSnapshot", long.class).invoke(myController, 0L/*ProfilingModes.SNAPSHOT_WITHOUT_HEAP*/);
      System.err.println("CPU Snapshot captured: " + path);
      try {
        getAPIMethod(Arrays.asList("stopCpuProfiling", "stopCPUProfiling")).invoke(myController);
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

  private Method getAPIMethod(String name, Class<?>... paramTypes) throws NoSuchMethodException {
    return getAPIMethod(Collections.singleton(name), paramTypes);
  }
  
  private Method getAPIMethod(Iterable<String> methodNameVariants, Class<?>... paramTypes) throws NoSuchMethodException {
    NoSuchMethodException ex = null;
    for (String name : methodNameVariants) {
      try {
        return myControllerClass.getDeclaredMethod(name, paramTypes);
      }
      catch (NoSuchMethodException e) {
        if (ex != null) {
          ex = e;
        }
      }
    }
    throw ex;
  }
}
