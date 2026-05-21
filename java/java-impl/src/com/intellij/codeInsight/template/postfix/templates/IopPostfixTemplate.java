// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class IopPostfixTemplate extends JavaEditablePostfixTemplate implements DumbAware {
  public IopPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("iop",
          "IO.println($EXPR$);$END$",
          "IO.println(expr)",
          Collections.singleton(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition()),
          JavaFeature.JAVA_LANG_IO.getMinimumLevel(), true, provider);
  }

  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return super.isApplicable(context, copyDocument, newOffset)
           && PsiUtil.isAvailable(JavaFeature.JAVA_LANG_IO, context)
           && !JavaPostfixTemplatesUtils.isInExpressionFile(context);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}
