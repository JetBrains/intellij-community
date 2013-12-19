package com.intellij.codeInsight.template.postfix.util;

import com.intellij.codeInsight.generation.surroundWith.JavaExpressionSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfExpressionSurrounder;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PostfixTemplatesUtils {
  private PostfixTemplatesUtils() {
  }

  public static void showErrorHint(Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, "Can't perform postfix completion", "Can't perform postfix completion", "");
  }

  public static void createSimpleStatement(@NotNull PsiElement context, @NotNull Editor editor, @NotNull String text) {
    PsiExpression expr = PostfixTemplate.getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    assert parent instanceof PsiStatement;
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    PsiStatement assertStatement = factory.createStatementFromText(text + " " + expr.getText() + ";", parent);
    PsiElement replace = parent.replace(assertStatement);
    editor.getCaretModel().moveToOffset(replace.getTextRange().getEndOffset());
  }

  @Contract("null -> false")
  public static boolean isIterable(@Nullable PsiType type) {
    return type != null && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE);
  }

  @Contract("null -> false")
  public static boolean isArray(@Nullable PsiType type) {
    return type != null && type instanceof PsiArrayType;
  }

  @Contract("null -> false")
  public static boolean isBoolean(@Nullable PsiType type) {
    return type != null && (PsiType.BOOLEAN.equals(type) || PsiType.BOOLEAN.equals(PsiPrimitiveType.getUnboxedType(type)));
  }

  @Contract("null -> false")
  public static boolean isNumber(@Nullable PsiType type) {
    if (type == null) {
      return false;
    }
    if (PsiType.INT.equals(type) || PsiType.BYTE.equals(type) || PsiType.LONG.equals(type)) {
      return true;
    }

    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
    return PsiType.INT.equals(unboxedType) || PsiType.BYTE.equals(unboxedType) || PsiType.LONG.equals(unboxedType);
  }

  @Nullable
  public static TextRange ifStatement(@NotNull Project project, @NotNull Editor editor, @NotNull PsiExpression expr) {
    JavaExpressionSurrounder surrounder = new JavaWithIfExpressionSurrounder();
    PsiElement[] elements = {expr};
    if (surrounder.isApplicable(elements)) {
      return surrounder.surroundElements(project, editor, elements);
    }
    else {
      showErrorHint(project, editor);
    }
    return null;
  }

  @Nullable
  public static TextRange apply(@NotNull JavaExpressionSurrounder surrounder, @NotNull Project project, @NotNull Editor editor, @NotNull PsiExpression expr) {
    PsiElement[] elements = {expr};
    if (surrounder.isApplicable(elements)) {
      return surrounder.surroundElements(project, editor, elements);
    }
    else {
      showErrorHint(project, editor);
    }
    return null;
  }
}

