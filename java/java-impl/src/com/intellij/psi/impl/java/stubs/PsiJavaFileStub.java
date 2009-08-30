/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.stubs.PsiClassHolderFileStub;

public interface PsiJavaFileStub extends PsiClassHolderFileStub<PsiJavaFile> {
  String getPackageName();
  boolean isCompiled();
}