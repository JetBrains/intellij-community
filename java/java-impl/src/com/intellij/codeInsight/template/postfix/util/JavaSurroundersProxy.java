package com.intellij.codeInsight.template.postfix.util;

import com.intellij.codeInsight.generation.surroundWith.JavaExpressionSurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author ignatov
 */
public class JavaSurroundersProxy {
  private static final Surrounder[] SURROUNDERS = new JavaExpressionSurroundDescriptor().getSurrounders();

  public static void cast(@NotNull Project project, @NotNull Editor editor, @NotNull PsiExpression expr)
    throws IncorrectOperationException {
    findAndApply("JavaWithCastSurrounder", project, editor, expr);
  }

  @Nullable
  public static TextRange ifStatement(@NotNull Project project, @NotNull Editor editor, @NotNull PsiExpression expr)
    throws IncorrectOperationException {
    return findAndApply("JavaWithIfExpressionSurrounder", project, editor, expr);
  }

  @Nullable
  private static TextRange findAndApply(@NotNull final String name,
                                        @NotNull Project project,
                                        @NotNull Editor editor,
                                        @NotNull PsiExpression expr) {
    Surrounder surrounder = ContainerUtil.find(SURROUNDERS, new Condition<Surrounder>() {
      @Override
      public boolean value(Surrounder surrounder) {
        return surrounder.getClass().getName().contains(name);
      }
    });

    PsiElement[] elements = {expr};
    if (surrounder != null) {
      if (surrounder.isApplicable(elements)) {
        return surrounder.surroundElements(project, editor, elements);
      }
      else {
        CommonUtils.showErrorHint(project, editor);
      }
    }
    else {
      throw new IncorrectOperationException("Can't find any applicable surrounder with elements: " + Arrays.toString(elements));
    }
    return null;
  }
}
