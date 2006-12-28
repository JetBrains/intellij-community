package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;

public class TestFileFilter extends FileFilter {
  private boolean myAreAllFilesUnderContentRoots = true;
  private VirtualFile[] myFilesNotUnderContentRoot = new VirtualFile[0];
  private VirtualFile[] myFilesWithAnallowedTypes = new VirtualFile[0];

  public TestFileFilter() {
    super(null, null);
  }

  @Override
  public boolean isUnderContentRoots(VirtualFile f) {
    if (!myAreAllFilesUnderContentRoots) return false;
    return !contains(myFilesNotUnderContentRoot, f);
  }

  public void dontAllowAnyFile() {
    myAreAllFilesUnderContentRoots = false;
  }

  public void setFilesNotUnderContentRoot(VirtualFile... f) {
    myFilesNotUnderContentRoot = f;
  }

  @Override
  public boolean isFileTypeAllowed(VirtualFile f) {
    return !contains(myFilesWithAnallowedTypes, f);
  }

  public void setFilesWithUnallowedTypes(VirtualFile... f) {
    myFilesWithAnallowedTypes = f;
  }

  private boolean contains(VirtualFile[] files, VirtualFile f) {
    return Arrays.asList(files).contains(f);
  }
}
