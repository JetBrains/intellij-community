// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotationSupport;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteral;
import org.jetbrains.annotations.NotNull;

public final class JavaAnnotationSupport implements PsiAnnotationSupport {
  @Override
  public @NotNull PsiLiteral createLiteralValue(@NotNull String value, @NotNull PsiElement context) {
    return (PsiLiteral)JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value) + "\"", null);
  }
}
