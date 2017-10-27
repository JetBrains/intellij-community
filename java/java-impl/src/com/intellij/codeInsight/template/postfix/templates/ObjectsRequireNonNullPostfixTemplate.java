// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NOT_PRIMITIVE;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorTopmost;

public class ObjectsRequireNonNullPostfixTemplate extends StringBasedPostfixTemplate {
  public ObjectsRequireNonNullPostfixTemplate() {
    super("reqnonnull", "Objects.requireNonNull(expr)", JavaPostfixTemplatesUtils.minimalLanguageLevelSelector(selectorTopmost(IS_NOT_PRIMITIVE),
                                                                                                               LanguageLevel.JDK_1_7));
  }

  @Nullable
  @Override
  public String getTemplateString(@NotNull PsiElement element) {
    return "java.util.Objects.requireNonNull($expr$)";
  }
}
