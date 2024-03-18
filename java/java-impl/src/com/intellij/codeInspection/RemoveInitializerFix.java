// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteSideEffectsAwareFix;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
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
    if (!(descriptor.getPsiElement() instanceof PsiExpression initializer)) return ModCommand.nop();
    if (!(initializer.getParent() instanceof PsiVariable variable)) return ModCommand.nop();
    List<ModCommandAction> subActions;
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(initializer);
    if (!sideEffects.isEmpty() && !ContainerUtil.exists(sideEffects, se -> VariableAccessUtils.variableIsUsed(variable, se))) {
      subActions = List.of(new SideEffectAwareRemove(initializer),
                           new DeleteElementFix(initializer, JavaBundle.message("delete.initializer.completely")));
    }
    else {
      subActions = List.of(new DeleteElementFix(initializer));
    }
    return ModCommand.chooseAction(JavaBundle.message("inspection.unused.assignment.remove.initializer.quickfix.title"), subActions);
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
      invoke(initializer);
    }

    private void invoke(@NotNull PsiExpression initializer) {
      PsiVariable origVar = initializer.getParent() instanceof PsiVariable v ? v:
                            initializer.getParent() instanceof PsiAssignmentExpression assignment &&
                            assignment.getLExpression() instanceof PsiReferenceExpression ref && 
                            ref.resolve() instanceof PsiVariable v ? v : null;
      List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(
        initializer, e -> e instanceof PsiUnaryExpression unary && ExpressionUtils.isReferenceTo(unary.getOperand(), origVar) ||
                          e instanceof PsiAssignmentExpression assignment &&
                          ExpressionUtils.isReferenceTo(assignment.getLExpression(), origVar));
      CodeBlockSurrounder.SurroundResult result = null;
      if (!sideEffects.isEmpty()) {
        PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, initializer);
        CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(initializer);
        if (surrounder == null) {
          tryProcessExpressionList(initializer, sideEffects);
          return;
        }
        result = surrounder.surround();
        PsiStatement anchor = result.getAnchor();
        initializer = result.getExpression();
        if (statements.length > 0) {
          BlockUtils.addBefore(anchor, statements);
        }
      }
      PsiElement parent = initializer.getParent();
      if (parent instanceof PsiVariable var) {
        initializer.delete();
        if (myAction != null) {
          myAction.accept(var);
        }
      } else if (parent instanceof PsiAssignmentExpression) {
        RemoveUnusedVariableUtil.deleteWholeStatement(parent);
      }
      if (result != null) {
        result.collapse();
      }
    }

    private static void tryProcessExpressionList(@NotNull PsiExpression initializer, List<PsiExpression> sideEffects) {
      if (initializer.getParent() instanceof PsiAssignmentExpression assignment) {
        if (assignment.getParent() instanceof PsiExpressionList list) {
          for (PsiExpression effect : sideEffects) {
            list.addBefore(effect, assignment);
          }
          assignment.delete();
        }
        if (assignment.getParent() instanceof PsiExpressionStatement statement && statement.getParent() instanceof PsiForStatement) {
          if (sideEffects.size() == 1) {
            assignment.replace(sideEffects.get(0));
          } else {
            PsiExpressionListStatement listStatement = (PsiExpressionListStatement)JavaPsiFacade.getElementFactory(statement.getProject())
              .createStatementFromText("a,b", null);
            PsiExpressionList list = listStatement.getExpressionList();
            PsiExpression[] mockExpressions = list.getExpressions();
            PsiExpression first = mockExpressions[0];
            for (PsiExpression effect : sideEffects) {
              list.addBefore(effect, first);
            }
            for (PsiExpression expression : mockExpressions) {
              expression.delete();
            }
            statement.replace(listStatement);
          }
        }
      }
    }

    public static void remove(@NotNull PsiExpression expression) {
      new SideEffectAwareRemove(expression).invoke(expression);
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
