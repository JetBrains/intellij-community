// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public final class JavaFoldingBuilder extends JavaFoldingBuilderBase {
  @Override
  protected boolean isBelowRightMargin(@NotNull PsiFile file, int lineLength) {
    final CodeStyleSettings settings = CodeStyle.getSettings(file);
    return lineLength <= settings.getRightMargin(JavaLanguage.INSTANCE);
  }

  @Override
  protected boolean shouldShowExplicitLambdaType(@NotNull PsiAnonymousClass anonymousClass, @NotNull PsiNewExpression expression) {
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiAssignmentExpression) {
      return true;
    }

    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, false);
    return types.length != 1 || !types[0].getType().equals(anonymousClass.getBaseClassType());
  }

  @Override
  protected @NotNull String rightArrow() {
    return getRightArrow();
  }

  public static @NotNull String getRightArrow() {
    return EditorUtil.displayCharInEditor('\u2192', EditorColors.FOLDED_TEXT_ATTRIBUTES, "->");
  }
}

