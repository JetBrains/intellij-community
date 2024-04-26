// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

public final class JavaOutOfSourcesChecker implements OutOfSourcesChecker {

  @Override
  public @NotNull FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  public boolean isOutOfSources(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    return !index.isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.SOURCES);
  }
}
