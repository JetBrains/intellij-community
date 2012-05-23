package com.intellij.openapi.project;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

/**
 * Author: dmitrylomov
 */
public class ProjectCoreUtil {
  @NonNls public static final String DIRECTORY_BASED_PROJECT_DIR = ".idea";

  public static boolean isProjectOrWorkspaceFile(final VirtualFile file) {
    return isProjectOrWorkspaceFile(file, file.getFileType());
  }

  public static boolean isProjectOrWorkspaceFile(final VirtualFile file,
                                                 final FileType fileType) {
    if (fileType instanceof InternalFileType) return true;
    return file.getPath().contains("/"+ DIRECTORY_BASED_PROJECT_DIR +"/");
  }
}
