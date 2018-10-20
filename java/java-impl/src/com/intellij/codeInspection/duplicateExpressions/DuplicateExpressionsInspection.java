// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class DuplicateExpressionsInspection extends LocalInspectionTool {
  public int complexityThreshold = 70;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);

        if (expression instanceof PsiParenthesizedExpression) {
          return;
        }
        DuplicateExpressionsContext context = DuplicateExpressionsContext.getOrCreateContext(expression, session);
        if (context == null || context.mayHaveSideEffect(expression)) {
          return;
        }
        if (context.getComplexity(expression) > complexityThreshold) {
          context.addOccurrence(expression);
        }
      }

      @Override
      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);

        PsiCodeBlock body = method.getBody();
        if (body != null) {
          filterExpressions(body, session);
        }
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);

        filterExpressions(initializer.getBody(), session);
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);

        PsiElement body = expression.getBody();
        if (body instanceof PsiCodeBlock) {
          filterExpressions((PsiCodeBlock)body, session);
        }
      }

      public void filterExpressions(@NotNull PsiCodeBlock body, @NotNull UserDataHolder session) {
        DuplicateExpressionsContext context = DuplicateExpressionsContext.getContext(body, session);
        if (context == null) return;

        Set<PsiExpression> processed = new THashSet<>();
        context.forEach((pattern, occurrences) -> {
          if (!processed.contains(pattern)) {
            processed.addAll(occurrences);
            if (occurrences.size() > 1 && areSafeToExtract(occurrences, body)) {
              for (PsiExpression occurrence : occurrences) {
                holder.registerProblem(occurrence, InspectionsBundle.message("inspection.duplicate.expressions.message"));
              }
            }
          }
        });
      }
    };
  }

  private static boolean areSafeToExtract(@NotNull List<PsiExpression> occurrences, @NotNull PsiCodeBlock body) {
    if (occurrences.isEmpty()) return false;
    Project project = occurrences.get(0).getProject();
    Set<PsiVariable> variables = collectVariablesSafeToExtract(occurrences);
    if (variables == null) return false;
    if (variables.isEmpty()) return true;

    PsiElement anchor = RefactoringUtil.getAnchorElementForMultipleExpressions(occurrences.toArray(PsiExpression.EMPTY_ARRAY), null);
    if (anchor == null) return false;
    PsiElement anchorParent = anchor.getParent();
    if (anchorParent == null) return false;
    try {
      ControlFlow flow = ControlFlowFactory.getInstance(project).getControlFlow(body, new LocalsControlFlowPolicy(body));
      int startOffset = flow.getSize();
      int endOffset = 0;

      for (PsiExpression occurrence : occurrences) {
        PsiElement occurrenceSurroundings = PsiTreeUtil.findFirstParent(occurrence, false, e -> e.getParent() == anchorParent);
        if (occurrenceSurroundings == null) return false;
        startOffset = Math.min(startOffset, flow.getStartOffset(occurrenceSurroundings));
        endOffset = Math.max(endOffset, flow.getEndOffset(occurrenceSurroundings));
      }
      return ControlFlowUtil.areVariablesUnmodifiedAtLocations(flow, startOffset, endOffset, variables, occurrences);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
  }

  @Nullable
  private static Set<PsiVariable> collectVariablesSafeToExtract(@NotNull List<PsiExpression> occurrences) {
    Set<PsiVariable> variables = new THashSet<>();
    Ref<Boolean> refFailed = new Ref<>(Boolean.FALSE);
    JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);

        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) {
          variables.add((PsiVariable)resolved);
        }
        else if (resolved instanceof PsiVariable && !((PsiVariable)resolved).hasModifierProperty(PsiModifier.FINAL)) {
          refFailed.set(Boolean.TRUE);
          stopWalking();
        }
      }
    };

    for (PsiExpression occurrence : occurrences) {
      occurrence.accept(visitor);
      if (refFailed.get()) {
        return null;
      }
    }

    return variables;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(
      InspectionsBundle.message("inspection.duplicate.complexity.threshold"), this, "complexityThreshold", 3);
  }
}
