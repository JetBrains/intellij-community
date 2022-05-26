// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This class is initialized in two class loaders: the bootstrap classloader and the main IDEA classloader. The bootstrap instance
 * has ourMirrorClass initialized by the Bootstrap class; it calls the main instance of itself via reflection.
 */
public final class WindowsCommandLineProcessor {
  // The MainRunner class which is loaded in the main IDEA (non-bootstrap) classloader.
  public static Class<?> ourMainRunnerClass;

  /**
   * NOTE: This method is called through JNI by the Windows launcher. Please do not delete or rename it.
   */
  @SuppressWarnings("unused")
  public static int processWindowsLauncherCommandLine(final String currentDirectory, final String[] args) {
    if (ourMainRunnerClass != null) {
      try {
        Method method = ourMainRunnerClass.getMethod("processWindowsLauncherCommandLine", String.class, String[].class);
        return (Integer)method.invoke(null, currentDirectory, args);
      }
      catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) { }
    }
    return 1;
  }
}