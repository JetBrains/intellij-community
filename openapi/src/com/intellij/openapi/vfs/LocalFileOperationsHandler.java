package com.intellij.openapi.vfs;

import java.io.IOException;

/**
 * @author max
 */
public interface LocalFileOperationsHandler {
  boolean delete(VirtualFile file) throws IOException;
  boolean move(VirtualFile file, VirtualFile toDir) throws IOException;
  boolean rename(VirtualFile file, String newName) throws IOException;

  boolean createFile(VirtualFile dir, String name) throws IOException;
  boolean createDirectory(VirtualFile dir, String name) throws IOException;
}
