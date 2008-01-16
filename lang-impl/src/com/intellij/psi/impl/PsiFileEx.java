package com.intellij.psi.impl;

import com.intellij.ide.startup.FileContent;
import com.intellij.psi.PsiFile;

public interface PsiFileEx extends PsiFile{
  boolean isContentsLoaded();
  void onContentReload();
  PsiFile cacheCopy(final FileContent content); // See CacheUtil
}
