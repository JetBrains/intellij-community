package com.intellij.psi.stubs;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

/**
 * @author ilyas
 */
public interface PsiClassHolderFileStub<T extends PsiFile> extends PsiFileStub<T>{

  PsiClass[] getClasses();
}
