// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class SerrPostfixTemplate extends JavaEditablePostfixTemplate {
  public SerrPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("serr",
          "System.err.println($EXPR$);$END$",
          "System.err.println(expr)",
          Collections.singleton(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition()),
          LanguageLevel.JDK_1_3, true, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}
