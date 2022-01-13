// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;


public interface JavaDocHighlightingManager {

  @NotNull TextAttributes getKeywordAttributes();

  @NotNull TextAttributes getCommaAttributes();

  @NotNull TextAttributes getParenthesesAttributes();

  @NotNull TextAttributes getDotAttributes();

  @NotNull TextAttributes getBracketsAttributes();

  @NotNull TextAttributes getOperationSignAttributes();

  @NotNull TextAttributes getClassNameAttributes();

  @NotNull TextAttributes getClassDeclarationAttributes(@NotNull PsiClass aClass);

  @NotNull TextAttributes getMethodDeclarationAttributes(@NotNull PsiMethod method);

  @NotNull TextAttributes getFieldDeclarationAttributes(@NotNull PsiField field);

  @NotNull TextAttributes getParameterAttributes();

  @NotNull TextAttributes getTypeParameterNameAttributes();

  @NotNull TextAttributes getLocalVariableAttributes();

  @NotNull TextAttributes getMethodCallAttributes();
}
