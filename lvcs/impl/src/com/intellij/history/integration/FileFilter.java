package com.intellij.history.integration;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
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

  public boolean isUnderContentRoot(VirtualFile f) {
    if (!(f.getFileSystem() instanceof LocalFileSystem)) return false;
    return myFileIndex.isInContent(f);
  }

  public boolean isAllowed(VirtualFile f) {
    if (myTypeManager.isFileIgnored(f.getName())) return false;
    if (f.isDirectory()) return true;
    return !myTypeManager.getFileTypeByFile(f).isBinary();
  }
}
