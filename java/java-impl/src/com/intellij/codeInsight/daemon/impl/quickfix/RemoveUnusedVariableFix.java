// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.RemoveInitializerFix;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class RemoveUnusedVariableFix extends PsiBasedModCommandAction<PsiVariable> {
  public RemoveUnusedVariableFix(PsiVariable variable) {
    super(variable);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("remove.unused.element.family", JavaElementKind.VARIABLE.object());
  }

  protected @IntentionName @NotNull String getText(@NotNull PsiVariable variable) {
    return CommonQuickFixBundle.message("fix.remove.title.x", JavaElementKind.fromElement(variable).object(), variable.getName());
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiVariable variable) {
    String message = getText(variable);
    return Presentation.of(message);
  }

  static Map<PsiExpression, List<PsiExpression>> collectSideEffects(@NotNull PsiVariable variable,
                                                                    @NotNull List<@NotNull PsiReferenceExpression> references) {
    PsiExpression initializer = variable.getInitializer();
    Map<PsiExpression, List<PsiExpression>> result = new HashMap<>();
    if (initializer != null) {
      List<PsiExpression> expressions = SideEffectChecker.extractSideEffectExpressions(initializer);
      if (!expressions.isEmpty()) {
        result.put(initializer, expressions);
      }
    }
    for (PsiReferenceExpression reference : references) {
      if (reference.getParent() instanceof PsiAssignmentExpression assignment &&
          ExpressionUtils.isVoidContext(assignment)) {
        PsiExpression rExpression = assignment.getRExpression();
        if (rExpression != null) {
          List<PsiExpression> expressions = SideEffectChecker.extractSideEffectExpressions(rExpression);
          if (!expressions.isEmpty()) {
            result.put(rExpression, expressions);
          }
        }
      }
    }
    return result;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiVariable variable) {
    final List<PsiReferenceExpression> references = collectReferences(variable);
    Map<PsiExpression, List<PsiExpression>> effects = collectSideEffects(variable, references);
    if (effects.isEmpty()) {
      return new RemoveVariableSideEffectAware(variable, false).perform(context);
    }
    else {
      return ModCommand.chooseAction(JavaBundle.message("popup.title.remove.unused.variable"),
                                     new RemoveVariableSideEffectAware(variable, true),
                                     new RemoveVariableSideEffectAware(variable, false));
    }
  }

  private static class RemoveVariableSideEffectAware extends PsiUpdateModCommandAction<PsiVariable> {
    private final boolean myKeepSideEffects;

    protected RemoveVariableSideEffectAware(@NotNull PsiVariable variable, boolean keepSideEffects) {
      super(variable);
      myKeepSideEffects = keepSideEffects;
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiVariable variable) {
      Presentation presentation = Presentation.of(getFamilyName());
      if (myKeepSideEffects) {
        final List<PsiReferenceExpression> references = collectReferences(variable);
        Map<PsiExpression, List<PsiExpression>> effects = collectSideEffects(variable, references);
        return presentation.withHighlighting(StreamEx.ofValues(effects).flatCollection(Function.identity())
                                               .map(PsiExpression::getTextRange).toArray(TextRange.EMPTY_ARRAY));
      }
      return presentation;
    }

    @Override
    public @NotNull String getFamilyName() {
      return myKeepSideEffects ? 
             JavaBundle.message("intention.family.name.extract.possible.side.effects") : 
             JavaBundle.message("intention.family.name.delete.possible.side.effects");
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiVariable variable, @NotNull ModPsiUpdater updater) {
      boolean retry = true;
      while(retry) {
        retry = false;
        List<PsiReferenceExpression> refs = collectReferences(variable);
        for (PsiReferenceExpression ref : refs) {
          if (!ref.isValid()) {
            retry = true;
            break;
          }
          PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
          if (parent instanceof PsiUnaryExpression unary && ExpressionUtils.isVoidContext(unary)) {
            RemoveUnusedVariableUtil.deleteWholeStatement(parent);
          }
          else if (parent instanceof PsiAssignmentExpression assignment) {
            PsiExpression rExpression = assignment.getRExpression();
            if (ExpressionUtils.isVoidContext(assignment)) {
              if (rExpression != null && myKeepSideEffects) {
                RemoveInitializerFix.SideEffectAwareRemove.remove(rExpression);
              }
              else {
                RemoveUnusedVariableUtil.deleteWholeStatement(assignment);
              }
            }
            else if (rExpression != null) {
              PsiElement result = new CommentTracker().replaceAndRestoreComments(assignment, rExpression);
              if (result.getParent() instanceof PsiParenthesizedExpression parens &&
                  !ParenthesesUtils.areParenthesesNeeded(parens, true)) {
                parens.replace(result);
              }
            }
            else {
              RemoveUnusedVariableUtil.deleteWholeStatement(assignment);
            }
          }
        }
      }
      if (variable instanceof PsiField) {
        variable.normalizeDeclaration();
      }
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null && myKeepSideEffects) {
        RemoveInitializerFix.SideEffectAwareRemove.remove(initializer);
      }
      CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(variable.getInitializer()); // assume that initializer is used (e.g. inlined)
      if (variable instanceof PsiJavaDocumentedElement docElement) {
        tracker.markUnchanged(docElement.getDocComment());
      }
      tracker.deleteAndRestoreComments(variable);
    }
  }

  private static @Unmodifiable List<PsiReferenceExpression> collectReferences(@NotNull PsiVariable variable) {
    List<PsiReferenceExpression> references = new ArrayList<>(VariableAccessUtils.getVariableReferences(variable));
    references.removeIf(ref -> !PsiUtil.isAccessedForWriting(ref));
    return ContainerUtil.filter(references, r1 ->
      (r1.getParent() instanceof PsiAssignmentExpression assignment && !ExpressionUtils.isVoidContext(assignment)) ||  
      !ContainerUtil.exists(references, r2 -> r1 != r2 && PsiTreeUtil.isAncestor(r2.getParent(), r1, true)));
  }
}
