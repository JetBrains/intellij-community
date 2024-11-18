// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.openapi.project.DumbAware;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class LocalDateToJavaSqlDatePostfixTemplate extends JavaEditableTaggedPostfixTemplate implements DumbAware {
  public LocalDateToJavaSqlDatePostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("java.sql.Date.valueOf(exp)",
          "sqlValueOf",
          "java.sql.Date.valueOf($EXPR$)$END$",
          "java.sql.Date.valueOf($EXPR$)",
          Collections.singleton(
            new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition("java.time.LocalDate")),
          LanguageLevel.JDK_1_8, false, new String[]{".toSqlDate"}, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}
