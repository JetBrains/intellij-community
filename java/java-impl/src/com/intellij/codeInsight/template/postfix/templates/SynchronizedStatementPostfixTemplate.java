// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class SynchronizedStatementPostfixTemplate extends JavaEditablePostfixTemplate {
  public SynchronizedStatementPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("synchronized",
          "synchronized ($EXPR$) {\n$END$\n}",
          "synchronized (expr)",
          Collections.singleton(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNotPrimitiveTypeExpressionCondition()),
          LanguageLevel.JDK_1_3, true, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}