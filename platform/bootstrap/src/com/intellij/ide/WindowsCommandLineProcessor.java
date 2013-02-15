/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This class is initialized in two classloaders: the bootstrap classloader and the main IDEA classloader. The bootstrap instance
 * has ourMirrorClass initialized by the Bootstrap class; it calls the main instance of itself via reflection.
 *
 * @author yole
 */
@SuppressWarnings("UnusedDeclaration")
public class WindowsCommandLineProcessor {
  // The WindowsCommandLineProcessor class which is loaded in the main IDEA (non-bootstrap) classloader.
  public static Class ourMirrorClass = null;

  public static WindowsCommandLineListener LISTENER = null;

  /**
   * NOTE: This method is called through JNI by the Windows launcher. Please do not delete or rename it.
   */
  public static void processWindowsLauncherCommandLine(final String currentDirectory, final String commandLine) {
    if (ourMirrorClass != null) {
      try {
        Method method = ourMirrorClass.getMethod("processWindowsLauncherCommandLine", String.class, String.class);
        method.invoke(null, currentDirectory, commandLine);
      }
      catch (NoSuchMethodException e) {
      }
      catch (InvocationTargetException e) {
      }
      catch (IllegalAccessException e) {
      }
    }
    else {
      if (LISTENER != null) {
        LISTENER.processWindowsLauncherCommandLine(currentDirectory, commandLine);
      }
    }
  }
}
