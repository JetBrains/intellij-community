package com.intellij.openapi.project;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Author: dmitrylomov
 */
public class ProjectCoreUtil {
  public static final String DIRECTORY_BASED_PROJECT_DIR = ".idea";

  public static boolean isProjectOrWorkspaceFile(final VirtualFile file) {
    return isProjectOrWorkspaceFile(file, file.getFileType());
  }

  public static boolean isProjectOrWorkspaceFile(final VirtualFile file,
                                                 final @Nullable FileType fileType) {
    if (fileType instanceof InternalFileType) return true;
    VirtualFile parent = file.isDirectory() ? file: file.getParent();
    while (parent != null) {
      if (Comparing.equal(parent.getNameSequence(), DIRECTORY_BASED_PROJECT_DIR, SystemInfoRt.isFileSystemCaseSensitive)) return true;
      parent = parent.getParent();
    }
    return false;
  }
}
