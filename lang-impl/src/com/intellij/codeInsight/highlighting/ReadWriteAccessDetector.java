package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

/**
 * @author yole
 */
public interface ReadWriteAccessDetector {
  ExtensionPointName<ReadWriteAccessDetector> EP_NAME = ExtensionPointName.create("com.intellij.readWriteAccessDetector");

  boolean isReadWriteAccessible(PsiElement element);
  boolean isWriteAccess(final PsiElement referencedElement, PsiReference reference);
}
