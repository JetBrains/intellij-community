package com.intellij.lang.surroundWith;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public interface Surrounder {
  String getTemplateDescription();

  /**
   * @return true if elements can be surrounded
   */
  boolean isApplicable(@NotNull PsiElement[] elements);

  /**
   * @return range to select/to position the caret
   */
  @Nullable TextRange surroundElements(@NotNull Project project,
                                       @NotNull Editor editor,
                                       @NotNull PsiElement[] elements) throws IncorrectOperationException;
}
