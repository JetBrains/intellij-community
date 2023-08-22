// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

public abstract class LibraryPresentationManager {
  public static LibraryPresentationManager getInstance() {
    return ApplicationManager.getApplication().getService(LibraryPresentationManager.class);
  }

  @NotNull
  public abstract Icon getNamedLibraryIcon(@NotNull Library library, @Nullable StructureConfigurableContext context);

  @Nullable
  public abstract Icon getCustomIcon(@NotNull Library library, @Nullable StructureConfigurableContext context);

  @NotNull
  public abstract List<Icon> getCustomIcons(@NotNull Library library, @Nullable StructureConfigurableContext context);

  @NotNull
  public abstract List<@NlsSafe String> getDescriptions(@NotNull Library library, StructureConfigurableContext context);

  @NotNull
  public abstract List<@Nls String> getDescriptions(VirtualFile @NotNull [] classRoots, Set<? extends LibraryKind> excludedKinds);

  public abstract List<Library> getLibraries(@NotNull Set<? extends LibraryKind> kinds, @NotNull Project project, @Nullable StructureConfigurableContext context);

  public abstract boolean isLibraryOfKind(@NotNull List<? extends VirtualFile> files, @NotNull LibraryKind kind);

  public abstract boolean isLibraryOfKind(@NotNull Library library, @NotNull LibrariesContainer librariesContainer,
                                          @NotNull Set<? extends LibraryKind> acceptedKinds);
}
