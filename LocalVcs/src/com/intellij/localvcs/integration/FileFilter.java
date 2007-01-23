package com.intellij.localvcs.integration;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.VirtualFile;

public class FileFilter {
  public static final long MAX_FILE_SIZE = 100 * 1024;

  private FileIndex myFileIndex;
  private FileTypeManager myTypeManager;

  public FileFilter(FileIndex fi, FileTypeManager tm) {
    myFileIndex = fi;
    myTypeManager = tm;
  }

  public boolean isAllowedAndUnderContentRoot(VirtualFile f) {
    return isUnderContentRoot(f) && isAllowed(f);
  }

  public boolean isAllowed(VirtualFile f) {
    return isFileTypeAllowed(f) && isFileSizeAllowed(f);
  }

  public boolean isUnderContentRoot(VirtualFile f) {
    return myFileIndex.isInContent(f);
  }

  protected boolean isFileTypeAllowed(VirtualFile f) {
    if (f.isDirectory()) return true;
    return !myTypeManager.getFileTypeByFile(f).isBinary();
  }

  private boolean isFileSizeAllowed(VirtualFile f) {
    if (f.isDirectory()) return true;
    return f.getLength() < MAX_FILE_SIZE;
  }
}
