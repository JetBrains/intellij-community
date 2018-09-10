// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class SoutvPostfixTemplate extends JavaEditablePostfixTemplate {
  public SoutvPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("soutv",
          "System.out.println(\"$EXPR_COPY$ = \" + $EXPR$);$END$",
          "System.out.println(expr)",
          Collections.singleton(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition()),
          LanguageLevel.JDK_1_3, true, provider);
  }

  @Override
  protected void addTemplateVariables(@NotNull PsiElement element, @NotNull Template template) {
    super.addTemplateVariables(element, template);
    template.addVariable("EXPR_COPY", "escapeString(EXPR)", null, false);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}
