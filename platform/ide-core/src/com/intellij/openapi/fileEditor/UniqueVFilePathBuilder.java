// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;


public abstract class UniqueVFilePathBuilder {
  private static final UniqueVFilePathBuilder DUMMY_BUILDER = new UniqueVFilePathBuilder() {
    @Override
    public @NotNull String getUniqueVirtualFilePath(@NotNull Project project, @NotNull VirtualFile vFile) {
      return vFile.getPresentableName();
    }

    @Override
    public @NotNull String getUniqueVirtualFilePathWithinOpenedFileEditors(@NotNull Project project, @NotNull VirtualFile vFile) {
      return vFile.getPresentableName();
    }
  };

  public static UniqueVFilePathBuilder getInstance() {
    UniqueVFilePathBuilder service = ApplicationManager.getApplication().getService(UniqueVFilePathBuilder.class);
    return service != null ? service : DUMMY_BUILDER;
  }

  public @NotNull @NlsSafe String getUniqueVirtualFilePath(@NotNull Project project, @NotNull VirtualFile vFile, @NotNull GlobalSearchScope scope) {
    return getUniqueVirtualFilePath(project, vFile);
  }

  public abstract @NotNull @NlsSafe String getUniqueVirtualFilePath(@NotNull Project project, @NotNull VirtualFile vFile);

  public abstract @NotNull @NlsSafe String getUniqueVirtualFilePathWithinOpenedFileEditors(@NotNull Project project, @NotNull VirtualFile vFile);
}