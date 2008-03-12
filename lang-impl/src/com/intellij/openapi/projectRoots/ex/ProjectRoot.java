package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author mike
 */
public interface ProjectRoot {
  boolean isValid();
  VirtualFile[] getVirtualFiles();

  String[] getUrls();

  String getPresentableString();

  void update();
}
