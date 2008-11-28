package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

/**
 * A reference which can be affected by a "Change Signature" refactoring.
 *
 * @author yole
 * @since 8.1
 */
public interface PsiCallReference extends PsiReference {
  PsiElement handleChangeSignature(ChangeInfo changeInfo);
}
