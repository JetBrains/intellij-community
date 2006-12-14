package com.intellij.localvcs.integration;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.VirtualFile;

public class FileFilter {
  private FileIndex myFileIndex;
  private FileTypeManager myTypeManager;

  public FileFilter(FileIndex fi, FileTypeManager tm) {
    myFileIndex = fi;
    myTypeManager = tm;
  }

  public boolean isFileAllowed(VirtualFile f) {
    if (!myFileIndex.isInContent(f)) return false;
    if (f.isDirectory()) return true;

    return !myTypeManager.getFileTypeByFile(f).isBinary();
  }
}
