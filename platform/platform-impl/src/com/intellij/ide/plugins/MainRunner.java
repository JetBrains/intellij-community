// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.WindowsCommandLineProcessor;
import com.intellij.idea.CommandLineArgs;
import com.intellij.idea.Main;
import com.intellij.idea.StartupUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.function.BiFunction;

public final class MainRunner  {
  @SuppressWarnings("StaticNonFinalField")
  public static BiFunction<String, String[], Integer> LISTENER = (integer, s) -> Main.ACTIVATE_NOT_INITIALIZED;

  @SuppressWarnings("StaticNonFinalField")
  public static Activity startupStart;

  /** Called via reflection from {@link Main#bootstrap}. */
  public static void start(@NotNull String mainClass,
                           String @NotNull [] args,
                           @NotNull LinkedHashMap<String, Long> startupTimings) {
    StartUpMeasurer.addTimings(startupTimings, "bootstrap");
    startupStart = StartUpMeasurer.startMainActivity("app initialization preparation");

    Main.setFlags(args);
    CommandLineArgs.parse(args);

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
  public static int processWindowsLauncherCommandLine(String currentDirectory, String[] args) {
    return LISTENER.apply(currentDirectory, args);
  }
}