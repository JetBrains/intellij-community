// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.startup.multiProcess;

import com.intellij.openapi.application.CustomConfigMigrationOption;
import com.intellij.openapi.application.PathManager;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.idea.Main.customTargetDirectoryToImportConfig;
import static com.intellij.idea.Main.isConfigImportNeeded;

final class PerProcessPathCustomization {
  public static Path getStartupScriptDir() {
    return PathManager.getSystemDir().resolve("startup-script");
  }

  public static void prepareConfig(Path newConfig, Path oldConfigPath, boolean migratePlugins) {
    try {
      if (isConfigImportNeeded(oldConfigPath)) {
        customTargetDirectoryToImportConfig = oldConfigPath;
      }
      else if (migratePlugins) {
        // The config directory exists, but the plugins for the frontend process weren't migrated,
        // so we trigger importing of config from the local IDE to migrate the plugins.
        customTargetDirectoryToImportConfig = newConfig;
        new CustomConfigMigrationOption.MigratePluginsFromCustomPlace(oldConfigPath).writeConfigMarkerFile(newConfig);
      }
      CustomConfigFiles.prepareConfigDir(newConfig, oldConfigPath);
    }
    catch (IOException e) {
      System.err.println("Failed to prepare config directory " + newConfig);
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
  }
}
