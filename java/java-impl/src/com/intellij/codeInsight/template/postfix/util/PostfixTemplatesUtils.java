/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    CommonRefactoringUtil.showErrorHint(project, editor, "Can't expand postfix template", "Can't expand postfix template", "");
  }

  public static void createSimpleStatement(@NotNull PsiElement context, @NotNull Editor editor, @NotNull String text) {
    createStatement(context, editor, text + " ", "");
  }

  public static void createStatement(@NotNull PsiElement context, @NotNull Editor editor, @NotNull String prefix, @NotNull String suffix) {
    createStatement(context, editor, prefix, suffix, 0);
  }

  public static void createStatement(@NotNull PsiElement context, @NotNull Editor editor, @NotNull String prefix, @NotNull String suffix, int offset) {
    PsiExpression expr = PostfixTemplate.getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    assert parent instanceof PsiStatement;
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    PsiStatement statement = factory.createStatementFromText(prefix + expr.getText() + suffix + ";", parent);
    PsiElement replace = parent.replace(statement);
    editor.getCaretModel().moveToOffset(replace.getTextRange().getEndOffset() + offset);
  }

  @Contract("null -> false")
  public static boolean isNotPrimitiveTypeExpression(@Nullable PsiExpression expression) {
    return expression != null && !(expression.getType() instanceof PsiPrimitiveType);
  }

  @Contract("null -> false")
  public static boolean isIterable(@Nullable PsiType type) {
    return type != null && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE);
  }

  @Contract("null -> false")
  public static boolean isThrowable(@Nullable PsiType type) {
    return type != null && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE);
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
  public static boolean isNonVoid(@Nullable PsiType type) {
    return type != null && !PsiType.VOID.equals(type);
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

