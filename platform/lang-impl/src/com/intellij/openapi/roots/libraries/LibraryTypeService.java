// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class LibraryTypeService {
  public static LibraryTypeService getInstance() {
    return ApplicationManager.getApplication().getService(LibraryTypeService.class);
  }

  public abstract @Nullable NewLibraryConfiguration createLibraryFromFiles(@NotNull LibraryRootsComponentDescriptor descriptor,
                                                                           @NotNull JComponent parentComponent,
                                                                           @Nullable VirtualFile contextDirectory,
                                                                           @Nullable LibraryType<?> type,
                                                                           final @Nullable Project project);
}
