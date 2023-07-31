// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseSwitchFix extends PsiUpdateModCommandAction<PsiSwitchBlock> {
  public BaseSwitchFix(@NotNull PsiSwitchBlock block) {
    super(block);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiSwitchBlock startSwitch) {
    int offset = Math.min(context.offset(), startSwitch.getTextRange().getEndOffset() - 1);
    PsiSwitchBlock currentSwitch = PsiTreeUtil.getNonStrictParentOfType(context.file().findElementAt(offset), PsiSwitchBlock.class);
    return currentSwitch == startSwitch ? Presentation.of(getText(startSwitch)) : null;
  }

  protected @IntentionName String getText(@NotNull PsiSwitchBlock switchBlock) {
    return getFamilyName();
  }
}
