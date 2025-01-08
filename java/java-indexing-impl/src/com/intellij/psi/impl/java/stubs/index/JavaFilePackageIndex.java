package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.CharSequenceHashStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

public class JavaFilePackageIndex extends CharSequenceHashStubIndexExtension<PsiFile> {

  @Override
  public @NotNull StubIndexKey<CharSequence, PsiFile> getKey() {
    return JavaStubIndexKeys.FILE_PACKAGE_INDEX;
  }
}