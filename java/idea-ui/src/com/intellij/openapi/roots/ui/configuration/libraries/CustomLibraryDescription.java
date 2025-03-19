// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public abstract class CustomLibraryDescription {
  public @Nullable DownloadableLibraryType getDownloadableLibraryType() {
    return null;
  }

  public abstract @NotNull Set<? extends LibraryKind> getSuitableLibraryKinds();

  public abstract @Nullable NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, @Nullable VirtualFile contextDirectory);

  /**
   * Called when the user enables the use of a framework and there is no existing library for that framework. Can be used to create a new
   * library with default settings without prompting the user.
   */
  public @Nullable NewLibraryConfiguration createNewLibraryWithDefaultSettings(@Nullable VirtualFile contextDirectory) {
    return null;
  }

  public @NotNull LibrariesContainer.LibraryLevel getDefaultLevel() {
    return LibrariesContainer.LibraryLevel.PROJECT;
  }
}
