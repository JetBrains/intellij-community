// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.migration;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

public interface PredefinedMigrationProvider {
  ExtensionPointName<PredefinedMigrationProvider> EP_NAME = ExtensionPointName.create("com.intellij.predefinedMigrationMapProvider");

  /**
   * URL should point to the file with serialized migration map.
   *
   * The simplest way to prepare such map:
   * 1. Refactor|Migrate...
   * 2. Create new migration map with all settings needed
   * 3. Copy map's file from config/migration to the plugin's resources
   */
  @NotNull
  @Contract(pure = true)
  URL getMigrationMap();

  /**
   * Provide localized description for the migration
   */
  default @Nls String getDescription() {
    return null;
  }
}
