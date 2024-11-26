// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.openapi.project.DumbAware;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class JavaUtilDateToLocalDatePostfixTemplate extends JavaEditablePostfixTemplate implements DumbAware {
  public JavaUtilDateToLocalDatePostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("toLocalDate",
          "java.time.LocalDate.ofInstant($EXPR$.toInstant(), $END$)",
          "LocalDate.ofInstant(exp.toInstant(), zoneId)",
          Collections.singleton(
            new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition("java.util.Date")),
          LanguageLevel.JDK_1_8, false, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}
