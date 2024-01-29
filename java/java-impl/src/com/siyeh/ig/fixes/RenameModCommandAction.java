// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public final class RenameModCommandAction extends PsiUpdateModCommandAction<PsiNameIdentifierOwner> {
  public RenameModCommandAction(@NotNull PsiNameIdentifierOwner element) {
    super(element);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiNameIdentifierOwner element, @NotNull ModPsiUpdater updater) {
    updater.rename(element, ContainerUtil.createMaybeSingletonList(element.getName()));
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("rename.quickfix");
  }

  @Override
  protected @NotNull IntentionPreviewInfo generatePreview(ActionContext context, PsiNameIdentifierOwner element) {
    String what = UsageViewUtil.getType(element) + " '" + element.getName() + "'";
    String message = RefactoringBundle.message("rename.0.and.its.usages.preview.text", what);
    return new IntentionPreviewInfo.Html(HtmlChunk.text(message));
  }
}
