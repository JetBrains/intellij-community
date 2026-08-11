// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.application.options.CodeStyle;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.actions.lists.JoinOrSplit;
import com.intellij.openapi.editor.actions.lists.ListWithElements;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
final class JavaSplitJoinArrayInitializerContext extends AbstractJavaSplitJoinContext {

  @Override
  public @Nullable ListWithElements extractData(@NotNull PsiElement context) {
    PsiArrayInitializerExpression expression = PsiTreeUtil.getParentOfType(context, PsiArrayInitializerExpression.class, false);
    return expression == null ? null : new ListWithElements(expression, List.of(expression.getInitializers()));
  }

  @Override
  public boolean needHeadBreak(@NotNull ListWithElements data, @NotNull PsiElement firstElement, @NotNull JoinOrSplit mode) {
    return mode == JoinOrSplit.SPLIT &&
           CodeStyle.getLanguageSettings(firstElement.getContainingFile(), JavaLanguage.INSTANCE).ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE;
  }

  @Override
  public boolean needTailBreak(@NotNull ListWithElements data, @NotNull PsiElement lastElement, @NotNull JoinOrSplit mode) {
    return mode == JoinOrSplit.SPLIT &&
           CodeStyle.getLanguageSettings(lastElement.getContainingFile(), JavaLanguage.INSTANCE).ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE;
  }

  @Override
  public @NotNull String getSplitText(@NotNull ListWithElements data) {
    return JavaBundle.message("intention.family.put.array.initializer.expressions.on.separate.lines");
  }

  @Override
  public @NotNull String getJoinText(@NotNull ListWithElements data) {
    return JavaBundle.message("intention.family.put.array.initializer.expressions.on.one.line");
  }
}
