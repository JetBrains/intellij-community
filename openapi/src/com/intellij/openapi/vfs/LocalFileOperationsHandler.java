package com.intellij.openapi.vfs;

import java.io.IOException;

/**
 * @author max
 */
public interface LocalFileOperationsHandler {
  boolean canHandleFileOperation(VirtualFile file);

  FileOperation delete(VirtualFile file) throws IOException;
  FileOperation move(VirtualFile file, VirtualFile toDir) throws IOException;
  FileOperation rename(VirtualFile file, String newName) throws IOException;

  FileOperation createFile(VirtualFile dir, String name) throws IOException;
  FileOperation createDirectory(VirtualFile dir, String name) throws IOException;
}
