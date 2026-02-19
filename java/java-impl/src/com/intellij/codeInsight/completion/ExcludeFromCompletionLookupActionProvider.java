// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExcludeFromCompletionLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(@NotNull LookupElement lookupElement, @NotNull Lookup lookup, @NotNull Consumer<? super @NotNull LookupElementAction> consumer) {
    Object o = lookupElement.getObject();
    if (o instanceof PsiClassObjectAccessExpression) {
      o = PsiUtil.resolveClassInType(((PsiClassObjectAccessExpression)o).getOperand().getType());
    }

    if (o instanceof PsiClass clazz) {
      addExcludes(consumer, clazz, clazz.getQualifiedName());
    } else if (o instanceof PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        addExcludes(consumer, method, PsiUtil.getMemberQualifiedName(method));
      }
    } else if (o instanceof PsiField field) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        addExcludes(consumer, field, PsiUtil.getMemberQualifiedName(field));
      }
    }
  }

  private static void addExcludes(Consumer<? super LookupElementAction> consumer, PsiElement element, @Nullable String qname) {
    if (qname == null) return;
    final Project project = element.getProject();
    for (final String s : AddImportAction.getAllExcludableStrings(qname)) {
      consumer.consume(new ExcludeFromCompletionAction(project, s));
    }
  }
}
