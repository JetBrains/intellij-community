// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.application.options.CodeStyle;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.actions.lists.JoinOrSplit;
import com.intellij.openapi.editor.actions.lists.ListWithElements;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
final class JavaSplitJoinAnnotationAttributesContext extends AbstractJavaSplitJoinContext {
  @Override
  public @Nullable ListWithElements extractData(@NotNull PsiElement context) {
    PsiElement parent = PsiTreeUtil.getParentOfType(context, PsiAnnotationParameterList.class, PsiArrayInitializerMemberValue.class);
    return switch (parent) {
      case PsiAnnotationParameterList list -> new ListWithElements(parent, List.of(list.getAttributes()));
      case PsiArrayInitializerMemberValue value -> new ListWithElements(parent, List.of(value.getInitializers()));
      case null, default -> null;
    };
  }

  @Override
  public boolean needHeadBreak(@NotNull ListWithElements data, @NotNull PsiElement firstElement, @NotNull JoinOrSplit mode) {
    if (mode == JoinOrSplit.JOIN) return false;
    return data.getList() instanceof PsiArrayInitializerMemberValue
           ? CodeStyle.getLanguageSettings(firstElement.getContainingFile(), JavaLanguage.INSTANCE).ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE
           : CodeStyle.getCustomSettings(firstElement.getContainingFile(), JavaCodeStyleSettings.class).NEW_LINE_AFTER_LPAREN_IN_ANNOTATION;
  }

  @Override
  public boolean needTailBreak(@NotNull ListWithElements data, @NotNull PsiElement lastElement, @NotNull JoinOrSplit mode) {
    if (mode == JoinOrSplit.JOIN) return false;
    return data.getList() instanceof PsiArrayInitializerMemberValue
           ? CodeStyle.getLanguageSettings(lastElement.getContainingFile(), JavaLanguage.INSTANCE).ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE
           : CodeStyle.getCustomSettings(lastElement.getContainingFile(), JavaCodeStyleSettings.class).RPAREN_ON_NEW_LINE_IN_ANNOTATION;
  }

  @Override
  public @NotNull String getJoinText(@NotNull ListWithElements data) {
    return data.getList() instanceof PsiArrayInitializerMemberValue
           ? JavaBundle.message("intention.family.put.array.initializer.expressions.on.one.line")
           : JavaBundle.message("intention.family.put.annotation.attributes.on.one.line");
  }

  @Override
  public @NotNull String getSplitText(@NotNull ListWithElements data) {
    return data.getList() instanceof PsiArrayInitializerMemberValue
           ? JavaBundle.message("intention.family.put.array.initializer.expressions.on.separate.lines")
           : JavaBundle.message("intention.family.put.annotation.attributes.on.separate.lines");
  }
}
