package com.intellij.refactoring.copy;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public interface CopyHandlerDelegate {
  ExtensionPointName<CopyHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.copyHandler");

  boolean canCopy(PsiElement[] elements);
  void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory);
  void doClone(PsiElement element);
}
