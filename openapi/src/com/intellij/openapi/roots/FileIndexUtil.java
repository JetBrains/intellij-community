package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FileIndexUtil {
  private FileIndexUtil() {
  }

  public static boolean isJavaSourceFile(@NotNull Project project, @NotNull VirtualFile file) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (file.isDirectory()) return false;
    if (fileTypeManager.getFileTypeByFile(file) != StdFileTypes.JAVA) return false;
    if (fileTypeManager.isFileIgnored(file.getName())) return false;
    return ProjectRootManager.getInstance(project).getFileIndex().isInSource(file);
  }
}
