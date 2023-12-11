// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExcludeFromCompletionLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(@NotNull LookupElement element, @NotNull Lookup lookup, @NotNull Consumer<? super @NotNull LookupElementAction> consumer) {
    Object o = element.getObject();
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

  private static void addExcludes(Consumer<? super LookupElementAction> consumer, PsiMember element, @Nullable String qname) {
    if (qname == null) return;
    final Project project = element.getProject();
    for (final String s : AddImportAction.getAllExcludableStrings(qname)) {
      consumer.consume(new ExcludeFromCompletionAction(project, s));
    }
  }

  private static class ExcludeFromCompletionAction extends LookupElementAction {
    private final Project myProject;
    private final String myToExclude;

    ExcludeFromCompletionAction(@NotNull Project project, @NotNull String s) {
      super(null, JavaBundle.message("exclude.0.from.completion", s));
      myProject = project;
      myToExclude = s;
    }

    @Override
    public Result performLookupAction() {
      AddImportAction.excludeFromImport(myProject, myToExclude);
      return Result.HIDE_LOOKUP;
    }
  }
}
