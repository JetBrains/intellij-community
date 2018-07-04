// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class AssertStatementPostfixTemplate extends JavaEditablePostfixTemplate {
  public AssertStatementPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("assert", "assert $EXPR$;$END$", "assert expr",
          Collections.singleton(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition()),
          LanguageLevel.JDK_1_4, true, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}