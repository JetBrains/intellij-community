package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwitchStatementPostfixTemplate extends StatementPostfixTemplateBase {
  public SwitchStatementPostfixTemplate() {
    super("switch", "Produces switch over integral/enum/string values", "switch (expr)");
  }

  private static boolean isSwitchCompatibleType(@Nullable PsiType type, @NotNull PsiElement context) {
    if (type == null) return false;
    if (PsiType.INT.isAssignableFrom(type)) return true;

    if (type instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null && psiClass.isEnum()) return true;
    }

    if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return true; // todo: mock jdk 6 and 7
      PsiFile containingFile = context.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        LanguageLevel level = ((PsiJavaFile)containingFile).getLanguageLevel();
        if (level.isAtLeast(LanguageLevel.JDK_1_7)) return true;
      }
    }

    return false;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expr = getTopmostExpression(context);
    return expr != null && expr.getParent() instanceof PsiExpressionStatement && isSwitchCompatibleType(expr.getType(), context);
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    surroundWith(context, editor, "switch");
  }
}
