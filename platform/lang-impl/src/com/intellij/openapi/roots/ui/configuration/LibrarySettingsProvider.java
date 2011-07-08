/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides configurables for library settings for certain library type (platform-based products).
 * @author Rustam Vishnyakov
 */
public abstract class LibrarySettingsProvider {
  public static final ExtensionPointName<LibrarySettingsProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.librarySettingsProvider");

  @NotNull
  public abstract LibraryType getLibraryType();
  public abstract Configurable getAdditionalSettingsConfigurable(Project project);

  @Nullable
  public static Configurable getAdditionalSettingsConfigurable(Project project, LibraryType libType) {
    LibrarySettingsProvider provider = forLibraryType(libType);
    if (provider == null) return null;
    return provider.getAdditionalSettingsConfigurable(project);
  }

  @Nullable
  public static LibrarySettingsProvider forLibraryType(LibraryType libType) {
    for (LibrarySettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (provider.getLibraryType().equals(libType)) {
        return provider;
      }
    }
    return null;
  }
}
