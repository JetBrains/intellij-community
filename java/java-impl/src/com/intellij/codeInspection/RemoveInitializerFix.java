// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteSideEffectsAwareFix;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class RemoveInitializerFix extends ModCommandQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("inspection.unused.assignment.remove.initializer.quickfix");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!(descriptor.getPsiElement() instanceof PsiExpression initializer)) return ModCommands.nop();
    if (!(initializer.getParent() instanceof PsiVariable variable)) return ModCommands.nop();
    List<ModCommandAction> subActions;
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(initializer);
    if (!sideEffects.isEmpty() && !ContainerUtil.exists(sideEffects, se -> VariableAccessUtils.variableIsUsed(variable, se))) {
      subActions = List.of(new SideEffectAwareRemove(initializer),
                           new DeleteElementFix(initializer, JavaBundle.message("delete.initializer.completely")));
    }
    else {
      subActions = List.of(new DeleteElementFix(initializer));
    }
    return new ModChooseAction(JavaBundle.message("inspection.unused.assignment.remove.initializer.quickfix.title"), subActions);
  }
  
  public static class SideEffectAwareRemove extends PsiUpdateModCommandAction<PsiExpression> {
    private final @Nullable Consumer<PsiVariable> myAction;

    public SideEffectAwareRemove(@NotNull PsiExpression initializer) {
      this(initializer, null);
    }

    public SideEffectAwareRemove(@NotNull PsiExpression initializer, @Nullable Consumer<PsiVariable> postAction) {
      super(initializer);
      myAction = postAction;
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression initializer, @NotNull ModPsiUpdater updater) {
      CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(initializer);
      if (surrounder == null) return;
      CodeBlockSurrounder.SurroundResult result = surrounder.surround();
      PsiStatement anchor = result.getAnchor();
      initializer = result.getExpression();
      List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(initializer);
      CommentTracker ct = new CommentTracker();
      sideEffects.forEach(ct::markUnchanged);
      PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, initializer);
      if (statements.length > 0) {
        BlockUtils.addBefore(anchor, statements);
      }
      PsiElement parent = initializer.getParent();
      if (parent instanceof PsiVariable var) {
        ct.deleteAndRestoreComments(initializer);
        if (myAction != null) {
          myAction.accept(var);
        }
      } else if (parent instanceof PsiAssignmentExpression) {
        ct.deleteAndRestoreComments(parent.getParent());
      }
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression initializer) {
      if (!CodeBlockSurrounder.canSurround(initializer)) return null;
      List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(initializer);
      return Presentation.of(DeleteSideEffectsAwareFix.getMessage(initializer, sideEffects))
        .withHighlighting(ContainerUtil.map2Array(sideEffects, TextRange.EMPTY_ARRAY, expression -> expression.getTextRange()));
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("extract.side.effects.family.name");
    }
  }
}
