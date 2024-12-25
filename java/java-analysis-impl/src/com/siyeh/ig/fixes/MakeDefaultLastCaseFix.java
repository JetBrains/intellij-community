// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeDefaultLastCaseFix extends PsiUpdateModCommandAction<PsiSwitchLabelStatementBase> {

  public MakeDefaultLastCaseFix(@NotNull PsiSwitchLabelStatementBase labelStatementBase) {
    super(labelStatementBase);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("make.default.the.last.case.family.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiSwitchLabelStatementBase element) {
    return Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiSwitchLabelStatementBase labelStatementBase, @NotNull ModPsiUpdater updater) {
    PsiSwitchBlock switchBlock = labelStatementBase.getEnclosingSwitchBlock();
    if (switchBlock == null) return;
    PsiCodeBlock blockBody = switchBlock.getBody();
    if (blockBody == null) return;
    PsiSwitchLabelStatementBase nextLabel =
      PsiTreeUtil.getNextSiblingOfType(labelStatementBase, PsiSwitchLabelStatementBase.class);//include comments and spaces
    if (nextLabel != null) {
      PsiElement lastStmtInDefaultCase = nextLabel.getPrevSibling();
      blockBody.addRange(labelStatementBase, lastStmtInDefaultCase);
      blockBody.deleteChildRange(labelStatementBase, lastStmtInDefaultCase);
    }
  }
}
