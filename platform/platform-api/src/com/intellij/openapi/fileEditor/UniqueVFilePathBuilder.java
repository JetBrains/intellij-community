// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class UniqueVFilePathBuilder {
  private static final UniqueVFilePathBuilder DUMMY_BUILDER = new UniqueVFilePathBuilder() {
    @NotNull
    @Override
    public String getUniqueVirtualFilePath(@NotNull Project project, @NotNull VirtualFile vFile) {
      return vFile.getPresentableName();
    }

    @NotNull
    @Override
    public String getUniqueVirtualFilePathWithinOpenedFileEditors(@NotNull Project project, @NotNull VirtualFile vFile) {
      return vFile.getPresentableName();
    }
  };

  public static UniqueVFilePathBuilder getInstance() {
    UniqueVFilePathBuilder service = ApplicationManager.getApplication().getService(UniqueVFilePathBuilder.class);
    return service != null ? service : DUMMY_BUILDER;
  }

  @NotNull
  public @NlsSafe String getUniqueVirtualFilePath(@NotNull Project project, @NotNull VirtualFile vFile, @NotNull GlobalSearchScope scope) {
    return getUniqueVirtualFilePath(project, vFile);
  }

  @NotNull
  public abstract @NlsSafe String getUniqueVirtualFilePath(@NotNull Project project, @NotNull VirtualFile vFile);

  @NotNull
  public abstract @NlsSafe String getUniqueVirtualFilePathWithinOpenedFileEditors(@NotNull Project project, @NotNull VirtualFile vFile);
}