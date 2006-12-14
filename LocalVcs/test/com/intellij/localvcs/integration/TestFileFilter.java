package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;

public class TestFileFilter extends FileFilter {
  private boolean myAreAllowed = true;

  public TestFileFilter() {
    super(null, null);
  }

  @Override
  public boolean isFileAllowed(VirtualFile f) {
    return myAreAllowed;
  }

  public void setAllFilesAllowance(boolean areAllowed) {
    myAreAllowed = areAllowed;
  }
}
