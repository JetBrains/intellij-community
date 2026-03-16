// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiLoopStatement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.ControlFlowSubRange;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.siyeh.ig.psiutils.SideEffectChecker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class IdempotentLoopBodyInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitWhileStatement(@NotNull PsiWhileStatement loop) {
        PsiExpression condition = loop.getCondition();
        if (condition == null || SideEffectChecker.mayHaveSideEffects(condition)) return;
        PsiStatement body = loop.getBody();
        if (body == null) return;
        if (SideEffectChecker.mayHaveNonLocalSideEffects(body)) return;
        final ControlFlow controlFlow;
        try {
          controlFlow =
            ControlFlowFactory.getInstance(loop.getProject()).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
        }
        catch (AnalysisCanceledException ignored) {
          return;
        }

        checkControlFlow(loop, controlFlow, holder);
      }

      @Override
      public void visitForStatement(@NotNull PsiForStatement loop) {
        PsiExpression condition = loop.getCondition();
        if (condition != null && SideEffectChecker.mayHaveSideEffects(condition)) return;
        PsiStatement body = loop.getBody();
        if (body == null) return;
        PsiStatement update = loop.getUpdate();
        if (SideEffectChecker.mayHaveNonLocalSideEffects(body) || update != null && SideEffectChecker.mayHaveNonLocalSideEffects(update)) return;
        ControlFlow controlFlow;
        try {
          controlFlow =
            ControlFlowFactory.getInstance(loop.getProject()).getControlFlow(loop, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
        }
        catch (AnalysisCanceledException ignored) {
          return;
        }
        int start = controlFlow.getStartOffset(body);
        int end = controlFlow.getEndOffset(update == null ? body : update);
        if(start == -1 || end == -1) return;
        controlFlow = new ControlFlowSubRange(controlFlow, start, end);

        checkControlFlow(loop, controlFlow, holder);
      }

      private static void checkControlFlow(PsiLoopStatement loop, ControlFlow bodyFlow, @NotNull ProblemsHolder holder) {
        Collection<PsiVariable> variables = ControlFlowUtil.getWrittenVariables(bodyFlow, 0, bodyFlow.getSize(), true);
        if (variables.isEmpty()) return;
        List<PsiReferenceExpression> reads = ControlFlowUtil.getReadBeforeWrite(bodyFlow);
        if (StreamEx.of(reads).map(PsiReferenceExpression::resolve).select(PsiVariable.class)
          .noneMatch(v -> v.hasModifierProperty(PsiModifier.VOLATILE) || variables.contains(v))) {
          holder.registerProblem(loop.getFirstChild(), JavaBundle.message("inspection.idempotent.loop.body"));
        }
      }
    };
  }
}
