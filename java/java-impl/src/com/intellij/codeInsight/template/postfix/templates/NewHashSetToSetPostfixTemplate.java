// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.openapi.project.DumbAware;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class NewHashSetToSetPostfixTemplate extends JavaEditableTaggedPostfixTemplate implements DumbAware {
  public NewHashSetToSetPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("new HashSet<>(exp)",
          "toSet",
          "new java.util.HashSet<>($EXPR$)$END$",
          "new HashSet<>($EXPR$)",
          Collections.singleton(
            new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition("java.util.Collection")),
          LanguageLevel.JDK_1_7, false, new String[]{".asSet"}, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }


}
