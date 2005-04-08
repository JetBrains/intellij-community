package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public interface PsiFileSystemItem extends PsiNamedElement {
  void checkSetName(String name) throws IncorrectOperationException;
}
