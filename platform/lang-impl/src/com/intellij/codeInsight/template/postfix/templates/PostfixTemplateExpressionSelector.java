// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Provides information about expressions available in the current postfix template context.
 *
 * @see PostfixTemplateWithExpressionSelector
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/advanced-postfix-templates.html">Advanced Postfix Templates (IntelliJ Platform Docs)</a>
 */
public interface PostfixTemplateExpressionSelector {

  /**
   * Checks whether the current context contains applicable expression that can be selected.
   */
  boolean hasExpression(@NotNull PsiElement context,
                        @NotNull Document copyDocument,
                        int newOffset);

  /**
   * Returns the list of all expressions applicable in the current context.
   * If the list size is greater than 1, then expression chooser popup is shown to a user.
   */
  @NotNull
  List<PsiElement> getExpressions(@NotNull PsiElement context,
                                  @NotNull Document document,
                                  int offset);

  /**
   * Returns a renderer for expressions returned from the {@link #getExpressions} method,
   * which is used to render template item in the expression chooser popup.
   */
  @NotNull
  Function<PsiElement, String> getRenderer();
}
