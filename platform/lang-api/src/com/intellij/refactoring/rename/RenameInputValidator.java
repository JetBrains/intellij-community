package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

/**
 * @author Gregory.Shrago
 */
public interface RenameInputValidator {
  boolean isInputValid(final String newName, final PsiElement element, final ProcessingContext context);
}
