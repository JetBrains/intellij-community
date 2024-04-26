// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTemplateExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public final class MissingStrProcessorFix extends PsiUpdateModCommandAction<PsiTemplateExpression> {
  public MissingStrProcessorFix(@NotNull PsiTemplateExpression template) {
    super(template);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.missing.str.processor");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiTemplateExpression template, @NotNull ModPsiUpdater updater) {
    if (template.getProcessor() == null && template.getTemplate() != null) {
      final PsiElement result = new CommentTracker()
        .replaceAndRestoreComments(template, CommonClassNames.JAVA_LANG_STRING_TEMPLATE + ".STR." + template.getText());
      JavaCodeStyleManager.getInstance(result.getProject()).shortenClassReferences(result);
    }
  }
}