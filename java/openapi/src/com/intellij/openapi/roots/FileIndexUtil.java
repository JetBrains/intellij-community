// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

public final class FileIndexUtil {
  private FileIndexUtil() {
  }

  public static boolean isJavaSourceFile(@NotNull Project project, @NotNull VirtualFile file) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (file.isDirectory() || !FileTypeRegistry.getInstance().isFileOfType(file, StdFileTypes.JAVA) || fileTypeManager.isFileIgnored(file)) {
      return false;
    }
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) || fileIndex.isInLibrarySource(file);
  }
}
