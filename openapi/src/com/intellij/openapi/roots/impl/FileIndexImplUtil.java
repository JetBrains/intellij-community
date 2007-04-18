package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;

public class FileIndexImplUtil {
  private FileIndexImplUtil() {
  }

  public static boolean iterateRecursively(VirtualFile root, VirtualFileFilter filter, ContentIterator iterator){
    if (!filter.accept(root)) return true;

    if (!iterator.processFile(root)) return false;

    if (root.isDirectory()){
      VirtualFile[] children = root.getChildren();
      for (VirtualFile aChildren : children) {
        if (!iterateRecursively(aChildren, filter, iterator)) return false;
      }
    }

    return true;
  }
}
