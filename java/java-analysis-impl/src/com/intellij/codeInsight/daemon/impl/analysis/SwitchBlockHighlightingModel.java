// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiSwitchBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Consumer;

import static com.intellij.java.codeserver.core.JavaPatternExhaustivenessUtil.hasExhaustivenessError;
import static java.util.Objects.requireNonNullElse;

public final class SwitchBlockHighlightingModel {
  public static boolean shouldAddDefault(@NotNull PsiSwitchBlock block) {
    if (!ExpressionUtil.isEnhancedSwitch(block)) return false;
    if (JavaPsiSwitchUtil.getUnconditionalPatternLabel(block) != null) return false;
    if (JavaPsiSwitchUtil.findDefaultElement(block) != null) return false;
    return hasExhaustivenessError(block);
  }

  static void checkExhaustiveness(@NotNull PsiSwitchBlock block, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return;

    if (!ExpressionUtil.isEnhancedSwitch(block)) return;
    if (JavaPsiSwitchUtil.getUnconditionalPatternLabel(block) != null) return;
    if (JavaPsiSwitchUtil.findDefaultElement(block) != null) return;
    if (!hasExhaustivenessError(block)) return;

    boolean hasAnyCaseLabels = JavaPsiSwitchUtil.hasAnyCaseLabels(block);
    @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String messageKey;
    boolean isSwitchExpr = block instanceof PsiExpression;
    if (hasAnyCaseLabels) {
      messageKey = isSwitchExpr ? "switch.expr.incomplete" : "switch.statement.incomplete";
    }
    else {
      messageKey = isSwitchExpr ? "switch.expr.empty" : "switch.statement.empty";
    }
    PsiElement anchor = requireNonNullElse(block.getExpression(), block.getFirstChild());
    HighlightInfo.Builder info =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(anchor).descriptionAndTooltip(JavaErrorBundle.message(messageKey));
    IntentionAction action = QuickFixFactory.getInstance().createAddSwitchDefaultFix(block, null);
    info.registerFix(action, null, null, null, null);
    HighlightFixUtil.addCompletenessFixes(block, fix -> info.registerFix(fix.asIntention(), null, null, null, null));
    errorSink.accept(info);
  }
}
