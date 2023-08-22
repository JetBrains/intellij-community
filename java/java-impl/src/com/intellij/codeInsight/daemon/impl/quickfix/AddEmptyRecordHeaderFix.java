// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiTypeParameterList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AddEmptyRecordHeaderFix extends PsiUpdateModCommandAction<PsiClass> {
  public AddEmptyRecordHeaderFix(@NotNull PsiClass record) {
    super(record);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass record, @NotNull ModPsiUpdater updater) {
    if (!record.isRecord() || record.getRecordHeader() != null) return;
    PsiTypeParameterList typeParameterList = record.getTypeParameterList();
    if (typeParameterList == null) return;
    PsiRecordHeader recordHeader = JavaPsiFacade.getElementFactory(context.project())
      .createRecordHeaderFromText("", record);
    record.addAfter(recordHeader, typeParameterList);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("insert.empty.parenthesis");
  }
}
