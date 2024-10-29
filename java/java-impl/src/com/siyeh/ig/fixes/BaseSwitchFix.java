// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.dataflow.CreateNullBranchFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

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


  protected static @Nullable PsiBasedModCommandAction<PsiSwitchBlock> createWithNull(@NotNull PsiSwitchBlock block,
                                                                                     @NotNull Supplier<? extends @Nullable BaseSwitchFix> producer) {
    PsiExpression expression = block.getExpression();
    Nullability nullability = NullabilityUtil.getExpressionNullability(expression, true);
    if (nullability != Nullability.NULLABLE) return null;
    List<PsiElement> branches = SwitchUtils.getSwitchBranches(block);
    if (ContainerUtil.or(branches,
                         branch -> branch instanceof PsiExpression psiExpression && ExpressionUtils.isNullLiteral(psiExpression))) {
      return null;
    }
    BaseSwitchFix action = producer.get();
    if (action == null) return null;
    BaseSwitchFix nullBranchFix = new CreateNullBranchFix(block, false);
    return new PsiBasedModCommandAction<>(block) {

      @Override
      public @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("create.missing.branches.with.null.branch.fix.family.name");
      }

      @Override
      protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiSwitchBlock element) {
        return ModCommand.psiUpdate(element, (e, upd) -> {
          action.invoke(context, e, upd);
          nullBranchFix.invoke(context, e, upd);
        });
      }
    };
  }
}
