// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.idea.StartupUtil;

/**
 * <b>NOTE:</b> This method is called through JNI by the Windows launcher. Please do not delete or rename it.
 */
@SuppressWarnings("unused")
public final class WindowsCommandLineProcessor {
  public static int processWindowsLauncherCommandLine(String currentDirectory, String[] args) {
    return StartupUtil.processWindowsLauncherCommandLine(currentDirectory, args);
  }
}
