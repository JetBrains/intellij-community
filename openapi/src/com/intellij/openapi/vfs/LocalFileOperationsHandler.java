package com.intellij.openapi.vfs;

import java.io.IOException;

/**
 * @author max
 */
public interface LocalFileOperationsHandler {
  boolean canHandleFileOperation(VirtualFile file);

  void delete(VirtualFile file) throws IOException;
  void move(VirtualFile file, VirtualFile toDir) throws IOException;
  void rename(VirtualFile file, String newName) throws IOException;

  void createFile(VirtualFile dir, String name) throws IOException;
  void createDirectory(VirtualFile dir, String name) throws IOException;
}
