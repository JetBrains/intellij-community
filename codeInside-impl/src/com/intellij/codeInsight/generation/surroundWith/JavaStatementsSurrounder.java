
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.psi.*;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.surroundWith.Surrounder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class JavaStatementsSurrounder implements Surrounder {
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    return true;
  }

  @Nullable public TextRange surroundElements(@NotNull Project project,
                                              @NotNull Editor editor,
                                              @NotNull PsiElement[] elements) throws IncorrectOperationException {
    return surroundStatements (project, editor, elements[0].getParent(), elements);
  }

 @Nullable protected abstract TextRange surroundStatements(final Project project, final Editor editor, final PsiElement parent, final PsiElement[] statements) throws IncorrectOperationException;
}