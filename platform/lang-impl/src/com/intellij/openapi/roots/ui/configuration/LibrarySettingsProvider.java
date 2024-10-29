// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryKind;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides configurables for library settings for certain library type (platform-based products).
 * @author Rustam Vishnyakov
 */
public abstract class LibrarySettingsProvider {
  public static final ExtensionPointName<LibrarySettingsProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.librarySettingsProvider");

  @Contract(pure = true)
  public abstract @NotNull LibraryKind getLibraryKind();
  @Contract(pure = true)
  public abstract Configurable getAdditionalSettingsConfigurable(Project project);

  @Contract(pure = true)
  public static @Nullable Configurable getAdditionalSettingsConfigurable(Project project, LibraryKind libKind) {
    LibrarySettingsProvider provider = forLibraryType(libKind);
    if (provider == null) return null;
    return provider.getAdditionalSettingsConfigurable(project);
  }

  @Contract(pure = true)
  public static @Nullable LibrarySettingsProvider forLibraryType(LibraryKind libType) {
    for (LibrarySettingsProvider provider : EP_NAME.getExtensionList()) {
      if (provider.getLibraryKind().equals(libType)) {
        return provider;
      }
    }
    return null;
  }
}
