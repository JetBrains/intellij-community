package com.intellij.psi.impl;

import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;

public interface PsiFileEx extends PsiFile{
  Key<Boolean> BATCH_REFERENCE_PROCESSING = Key.create("BATCH_REFERENCE_PROCESSING");

  boolean isContentsLoaded();
  void onContentReload();
  PsiFile cacheCopy(final FileContent content); // See CacheUtil
}
