// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

/**
 * @author yole
 */
public final class FileIndexUtil {
  private FileIndexUtil() {
  }

  public static boolean isJavaSourceFile(@NotNull Project project, @NotNull VirtualFile file) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (file.isDirectory() || file.getFileType() != StdFileTypes.JAVA || fileTypeManager.isFileIgnored(file)) {
      return false;
    }
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) || fileIndex.isInLibrarySource(file);
  }
}
