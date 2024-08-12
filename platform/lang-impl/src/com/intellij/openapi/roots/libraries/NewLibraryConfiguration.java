// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NewLibraryConfiguration {
  private final String myDefaultLibraryName;
  private final LibraryType<?> myLibraryType;
  private final LibraryProperties myProperties;

  protected NewLibraryConfiguration(@NotNull String defaultLibraryName) {
    this(defaultLibraryName, null, null);
  }

  protected <P extends LibraryProperties> NewLibraryConfiguration(@NotNull String defaultLibraryName, @Nullable LibraryType<P> libraryType, @Nullable P properties) {
    myDefaultLibraryName = defaultLibraryName;
    myLibraryType = libraryType;
    myProperties = properties;
  }

  public LibraryType<?> getLibraryType() {
    return myLibraryType;
  }

  public LibraryProperties getProperties() {
    return myProperties;
  }

  public @NotNull String getDefaultLibraryName() {
    return myDefaultLibraryName;
  }

  public abstract void addRoots(@NotNull LibraryEditor editor);
}
