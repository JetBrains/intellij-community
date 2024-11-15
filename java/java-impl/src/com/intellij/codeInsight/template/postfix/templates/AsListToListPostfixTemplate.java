// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.openapi.project.DumbAware;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class AsListToListPostfixTemplate extends JavaEditableTaggedPostfixTemplate implements DumbAware {
  public AsListToListPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("Arrays.asList(exp)",
          "asList",
          "java.util.Arrays.asList($EXPR$)$END$",
          "Arrays.asList($EXPR$)",
          Collections.singleton(
            new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayReferenceExpressionCondition()),
          LanguageLevel.JDK_1_3, false, new String[]{".toList"}, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }


}
