// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectUtilCore {
  public static @NotNull Project @NotNull [] getOpenProjects() {
    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    return projectManager == null ? new Project[0] : projectManager.getOpenProjects();
  }

  /**
   * @deprecated Use {@code com.intellij.ide.impl.ProjectUtil#isValidProjectPath}
   */
  @Deprecated
  public static boolean isValidProjectPath(@NotNull Path file) {
    return ProjectCoreUtil.isKnownProjectDirectory(file) ||
           (Strings.endsWith(file.toString(), ProjectFileType.DOT_DEFAULT_EXTENSION) && Files.isRegularFile(file));
  }

  @ApiStatus.Internal
  public static @Nullable VirtualFile getFileAndRefresh(@NotNull Path file) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString()));
    if (virtualFile == null || !virtualFile.isValid()) {
      return null;
    }

    virtualFile.refresh(false, false);
    return virtualFile;
  }
}
