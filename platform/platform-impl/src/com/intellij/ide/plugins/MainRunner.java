// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.WindowsCommandLineListener;
import com.intellij.ide.WindowsCommandLineProcessor;
import com.intellij.idea.Main;
import com.intellij.idea.StartupUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

public final class MainRunner  {
  @SuppressWarnings("StaticNonFinalField")
  public static WindowsCommandLineListener LISTENER;
  @SuppressWarnings("StaticNonFinalField")
  public static Activity startupStart;

  /** Called via reflection from {@link Main#bootstrap}. */
  public static void start(@NotNull String mainClass,
                            @NotNull String[] args,
                            @NotNull LinkedHashMap<String, Long> startupTimings) {
    StartUpMeasurer.addTimings(startupTimings, "bootstrap");

    startupStart = StartUpMeasurer.startMainActivity("app initialization preparation");

    Main.setFlags(args);

    ThreadGroup threadGroup = new ThreadGroup("Idea Thread Group") {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        StartupAbortedException.processException(e);
      }
    };

    new Thread(threadGroup, () -> {
      try {
        StartupUtil.prepareApp(args, mainClass);
      }
      catch (Throwable t) {
        StartupAbortedException.processException(t);
      }
    }, "Idea Main Thread").start();
  }

  /** Called via reflection from {@link WindowsCommandLineProcessor#processWindowsLauncherCommandLine}. */
  @SuppressWarnings("UnusedDeclaration")
  public static int processWindowsLauncherCommandLine(final String currentDirectory, final String[] args) {
    return LISTENER != null ? LISTENER.processWindowsLauncherCommandLine(currentDirectory, args) : 1;
  }
}