// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.idea.StartupUtil;

/**
 * This class is initialized in two class loaders: the bootstrap classloader and the main IDEA classloader. The bootstrap instance
 * has ourMirrorClass initialized by the Bootstrap class; it calls the main instance of itself via reflection.
 */
public final class WindowsCommandLineProcessor {
  /**
   * NOTE: This method is called through JNI by the Windows launcher. Please do not delete or rename it.
   */
  @SuppressWarnings("unused")
  public static int processWindowsLauncherCommandLine(final String currentDirectory, final String[] args) {
    StartupUtil.processWindowsLauncherCommandLine(currentDirectory, args);
    return 1;
  }
}