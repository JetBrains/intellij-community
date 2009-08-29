package com.intellij.execution.configurations;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public interface RefactoringListenerProvider {

  @Nullable
  RefactoringElementListener getRefactoringElementListener(final PsiElement element);

}
