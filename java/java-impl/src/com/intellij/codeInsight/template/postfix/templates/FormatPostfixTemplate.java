// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class FormatPostfixTemplate extends JavaEditablePostfixTemplate {
  public FormatPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("format",
          "String.format($EXPR$, $END$)",
          "String.format(expr)",
          Collections.singleton(
            new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(CommonClassNames.JAVA_LANG_STRING)),
          LanguageLevel.JDK_1_3, false, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}