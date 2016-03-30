/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/**
 * @author nik
 */
public abstract class CustomLibraryDescription {
  @Nullable
  public DownloadableLibraryType getDownloadableLibraryType() {
    return null;
  }

  @NotNull
  public abstract Set<? extends LibraryKind> getSuitableLibraryKinds();

  @Nullable
  public abstract NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, @Nullable VirtualFile contextDirectory);

  /**
   * Called when the user enables the use of a framework and there is no existing library for that framework. Can be used to create a new
   * library with default settings without prompting the user.
   */
  @Nullable
  public NewLibraryConfiguration createNewLibraryWithDefaultSettings(@Nullable VirtualFile contextDirectory) {
    return null;
  }

  @NotNull
  public LibrariesContainer.LibraryLevel getDefaultLevel() {
    return LibrariesContainer.LibraryLevel.PROJECT;
  }
}
