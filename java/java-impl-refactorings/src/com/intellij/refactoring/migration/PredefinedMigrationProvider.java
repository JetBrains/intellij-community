/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
  @Nls
  default String getDescription() {
    return null;
  }
}
