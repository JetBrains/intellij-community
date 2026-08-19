// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.configurationStore.ProjectStorePathManager;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectUtilCore {
  private ProjectUtilCore() { }

  @SuppressWarnings("SSBasedInspection")
  public static @NotNull Project @NotNull [] getOpenProjects() {
    var projectManager = ProjectManager.getInstanceIfCreated();
    return projectManager == null ? new Project[0] : projectManager.getOpenProjects();
  }

  /// @deprecated use [com.intellij.ide.impl.ProjectUtil#isValidProjectPathAsync]
  @Deprecated
  public static boolean isValidProjectPath(@NotNull Path file) {
    var storePathManager = ProjectStorePathManager.Companion.getInstance();
    return (
      storePathManager.testStoreDirectoryExistsForProjectRoot(file) ||
      Strings.endsWith(file.toString(), ProjectFileType.DOT_DEFAULT_EXTENSION) && Files.isRegularFile(file)
    );
  }

  @ApiStatus.Internal
  public static @Nullable VirtualFile getFileAndRefresh(@NotNull Path file) {
    var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    if (virtualFile == null || !virtualFile.isValid()) {
      return null;
    }
    virtualFile.refresh(false, false);
    return virtualFile;
  }
}
