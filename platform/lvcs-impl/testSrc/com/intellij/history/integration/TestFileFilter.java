package com.intellij.history.integration;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;

public class TestFileFilter extends FileFilter {
  private boolean myAreAllFilesAllowed = true;
  private VirtualFile[] myFilesNotUnderContentRoot = new VirtualFile[0];
  private VirtualFile[] myUnallowedFiles = new VirtualFile[0];

  public TestFileFilter() {
    super(null, null);
  }

  @Override
  public boolean isUnderContentRoot(VirtualFile f) {
    return !contains(myFilesNotUnderContentRoot, f);
  }

  public void setFilesNotUnderContentRoot(VirtualFile... f) {
    myFilesNotUnderContentRoot = f;
  }

  @Override
  public boolean isAllowed(VirtualFile f) {
    if (!myAreAllFilesAllowed) return false;
    return !contains(myUnallowedFiles, f);
  }

  public void dontAllowAnyFile() {
    myAreAllFilesAllowed = false;
  }

  public void setNotAllowedFiles(VirtualFile... f) {
    myUnallowedFiles = f;
  }

  private boolean contains(VirtualFile[] files, VirtualFile f) {
    return Arrays.asList(files).contains(f);
  }
}
