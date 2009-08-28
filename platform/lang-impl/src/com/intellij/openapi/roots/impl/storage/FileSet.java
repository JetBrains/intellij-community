package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public interface FileSet {
  void listFiles(List<VirtualFile> list);

  boolean hasChanged();

  void commit() throws IOException;

}
