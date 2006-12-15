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
    return isUnderContentRoots(f) && isFileTypeAllowed(f);
  }

  public boolean isUnderContentRoots(VirtualFile f) {
    return myFileIndex.isInContent(f);
  }

  public boolean isFileTypeAllowed(VirtualFile f) {
    if (f.isDirectory()) return true;
    return !myTypeManager.getFileTypeByFile(f).isBinary();
  }
}
