// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.generation.surroundWith.JavaWithIfExpressionSurrounder;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.*;

public class NotNullCheckPostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {

  public NotNullCheckPostfixTemplate() {
    this("notnull");
  }

  public NotNullCheckPostfixTemplate(String alias) {
    super(alias, "if (expr != null)", JAVA_PSI_INFO, selectorTopmost(IS_NOT_PRIMITIVE));
  }

  @Override
  protected @NotNull String getTail() {
    return "!= null";
  }

  @Override
  protected @NotNull Surrounder getSurrounder() {
    return new JavaWithIfExpressionSurrounder();
  }
}