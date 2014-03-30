package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import org.jetbrains.annotations.NotNull;

public class IntroduceFieldPostfixTemplate extends ExpressionPostfixTemplateWithChooser {
  public IntroduceFieldPostfixTemplate() {
    super("field", "Introduces field for expression", "myField = expr;");
  }

  @Override
  protected void doIt(@NotNull Editor editor, @NotNull PsiExpression expression) {
    IntroduceFieldHandler handler = ApplicationManager.getApplication().isUnitTestMode() ? getMockHandler(expression) : new IntroduceFieldHandler();
    handler.invoke(expression.getProject(), new PsiElement[]{expression}, null);
  }

  @NotNull
  private static IntroduceFieldHandler getMockHandler(@NotNull final PsiExpression expression) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    assert containingClass != null;

    return new IntroduceFieldHandler() {
      // mock default settings
      @Override
      protected Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass,
                                               PsiExpression expr, PsiType type, PsiExpression[] occurrences,
                                               PsiElement anchorElement, PsiElement anchorElementIfAll) {
        return new Settings(
          "foo", expression, PsiExpression.EMPTY_ARRAY, false, false, false,
          InitializationPlace.IN_CURRENT_METHOD, PsiModifier.PRIVATE, null,
          null, false, containingClass, false, false);
      }
    };
  }
}