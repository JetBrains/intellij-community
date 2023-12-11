// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.completion.SmartCompletionDecorator;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;

import java.util.List;


public final class JavaTemplateCompletionProcessor implements TemplateCompletionProcessor {
  @Override
  public boolean nextTabOnItemSelected(final ExpressionContext context, final LookupElement item) {
    final List<? extends PsiElement> elements = JavaCompletionUtil.getAllPsiElements(item);
    if (elements != null && elements.size() == 1 && elements.get(0) instanceof PsiPackage) {
      return false;
    }

    Editor editor = context.getEditor();
    if (editor != null && editor.getUserData(JavaMethodCallElement.ARGUMENT_TEMPLATE_ACTIVE) != null) {
      return item.as(ClassConditionKey.create(SmartCompletionDecorator.class)) != null;
    }

    return true;
  }
}
