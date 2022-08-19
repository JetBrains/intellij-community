// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.PathManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class Main {
  private Main() { }

  public static void main(String[] args) {
    LinkedHashMap<String, Long> startupTimings = new LinkedHashMap<>(6);
    startupTimings.put("startup begin", System.nanoTime());

    if (args.length == 1 && "%f".equals(args[0])) {
      //noinspection SSBasedInspection
      args = new String[0];
    }

    AppMode.setFlags(args);

    try {
      bootstrap(args, startupTimings);
    }
    catch (Throwable t) {
      StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), t);
      System.exit(AppExitCodes.STARTUP_EXCEPTION);
    }
  }

  private static void bootstrap(String[] args, LinkedHashMap<String, Long> startupTimings) throws Throwable {
    startupTimings.put("properties loading", System.nanoTime());
    PathManager.loadProperties();

    startupTimings.put("plugin updates install", System.nanoTime());
    // this check must be performed before system directories are locked
    if (!AppMode.isCommandLine() || Boolean.getBoolean(AppMode.FORCE_PLUGIN_UPDATES)) {
      boolean configImportNeeded = !AppMode.isHeadless() && !Files.exists(Path.of(PathManager.getConfigPath()));
      if (!configImportNeeded) {
        // Consider following steps:
        // - user opens settings, and installs some plugins;
        // - the plugins are downloaded and saved somewhere;
        // - IDE prompts for restart;
        // - after restart, the plugins are moved to proper directories ("installed") by the next line.
        // TODO get rid of this: plugins should be installed before restarting the IDE
        installPluginUpdates();
      }
    }

    startupTimings.put("classloader init", System.nanoTime());
    BootstrapClassLoaderUtil.initClassLoader(AppMode.isIsRemoteDevHost());
    StartUpMeasurer.addTimings(startupTimings, "bootstrap");
    StartupUtil.start(args);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static void installPluginUpdates() {
    try {
      // referencing `StartupActionScriptManager` is ok - a string constant will be inlined
      Path scriptFile = Path.of(PathManager.getPluginTempPath(), StartupActionScriptManager.ACTION_SCRIPT_FILE);
      if (Files.isRegularFile(scriptFile)) {
        // load StartupActionScriptManager and all others related class (ObjectInputStream and so on loaded as part of class define)
        // only if there is an action script to execute
        StartupActionScriptManager.executeActionScript();
      }
    }
    catch (IOException e) {
      StartupErrorReporter.showMessage("Plugin Installation Error",
                  "The IDE failed to install or update some plugins.\n" +
                  "Please try again, and if the problem persists, please report it\n" +
                  "to https://jb.gg/ide/critical-startup-errors\n\n" +
                  "The cause: " + e, false);
    }
  }
}
