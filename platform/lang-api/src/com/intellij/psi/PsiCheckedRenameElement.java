package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;

/**
 * @author yole
 */
public interface PsiCheckedRenameElement extends PsiNamedElement {
  /**
   * Checks if it is possible to rename the element to the specified name,
   * and throws an exception if the rename is not possible. Does not actually modify anything.
   *
   * @param name the new name to check the renaming possibility for.
   * @throws com.intellij.util.IncorrectOperationException if the rename is not supported or not possible for some reason.
   */
  void checkSetName(String name) throws IncorrectOperationException;
}
