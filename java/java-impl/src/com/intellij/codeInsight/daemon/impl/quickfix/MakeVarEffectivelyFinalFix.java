// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MakeVarEffectivelyFinalFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  private MakeVarEffectivelyFinalFix(@NotNull PsiLocalVariable variable) {
    super(variable);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!(startElement instanceof PsiLocalVariable local)) return;
    EffectivelyFinalFixer fixer = ContainerUtil.find(EffectivelyFinalFixer.values(), f -> f.isAvailable(local));
    if (fixer == null) return;
    fixer.fix(local);
  }

  @Override
  public @NotNull String getText() {
    return JavaAnalysisBundle.message("intention.name.make.variable.effectively.final");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  public static @Nullable MakeVarEffectivelyFinalFix createFix(@NotNull PsiVariable variable) {
    if (!(variable instanceof PsiLocalVariable local)) return null;
    if (!ContainerUtil.exists(EffectivelyFinalFixer.values(), f -> f.isAvailable(local))) return null;
    return new MakeVarEffectivelyFinalFix(local);
  }

  enum EffectivelyFinalFixer {
    MOVE_INITIALIZER_TO_IF_BRANCH {
      @Override
      boolean isAvailable(@NotNull PsiLocalVariable var) {
        PsiExpression initializer = var.getInitializer();
        if (!ExpressionUtils.isSafelyRecomputableExpression(initializer)) return false;
        if (refersToNonFinalLocal(initializer)) return false;
        Branched branched = extractInitMode(var);
        // Do not add too many branches
        return branched != null && branched.numberOfNonInitializedBranches() <= 3;
      }

      @Override
      void fix(@NotNull PsiLocalVariable var) {
        PsiExpression initializer = Objects.requireNonNull(var.getInitializer());
        Branched branched = Objects.requireNonNull(extractInitMode(var));
        PsiStatement statement = JavaPsiFacade.getElementFactory(var.getProject())
          .createStatementFromText(var.getName() + "=" + initializer.getText() + ";", null);
        branched.addInitializer(statement);
        initializer.delete();
      }
    };
    abstract boolean isAvailable(@NotNull PsiLocalVariable var);

    abstract void fix(@NotNull PsiLocalVariable var);
  }

  private static boolean refersToNonFinalLocal(PsiExpression initializer) {
    if (initializer instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiVariable refTarget) {
      if (PsiUtil.isJvmLocalVariable(refTarget) && !refTarget.hasModifierProperty(PsiModifier.FINAL)) {
        PsiElement block = PsiUtil.getVariableCodeBlock(refTarget, null);
        return block == null || !HighlightControlFlowUtil.isEffectivelyFinal(refTarget, block, null);
      }
    }
    return false;
  }

  @Nullable
  private static Branched extractInitMode(@NotNull PsiLocalVariable var) {
    List<PsiExpressionStatement> initializers = initializers(var);
    if (initializers.isEmpty()) return null;
    if (!(var.getParent() instanceof PsiDeclarationStatement decl)) return null;
    if (!(decl.getParent() instanceof PsiCodeBlock block)) return null;
    PsiElement commonParent = PsiTreeUtil.findCommonParent(initializers);
    PsiIfStatement ifStatement = getTopLevelIfStatement(block, commonParent);
    if (ifStatement == null) return null;
    InitMode mode = StreamEx.of(initializers)
      .map(initStatement -> InitMode.create(initStatement, ifStatement))
      .collect(MoreCollectors.reducingWithZero(ExactMode.BOTTOM, ExactMode.NOT_INITIALIZED, InitMode::join));
    if (!(mode instanceof Branched branched)) return null;
    return branched;
  }

  @Nullable
  private static PsiIfStatement getTopLevelIfStatement(PsiCodeBlock block, PsiElement commonParent) {
    while (true) {
      PsiIfStatement ifStatement = PsiTreeUtil.getNonStrictParentOfType(commonParent, PsiIfStatement.class);
      if (ifStatement == null) return null;
      PsiElement parent = ifStatement.getParent();
      if (parent == block) return ifStatement;
      commonParent = parent;
    }
  }

  private static @NotNull List<PsiExpressionStatement> initializers(@NotNull PsiLocalVariable var) {
    PsiElement block = PsiUtil.getVariableCodeBlock(var, null);
    if (block == null) return List.of();
    List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(var, block);
    List<PsiExpressionStatement> initializers = new ArrayList<>();
    for (PsiReferenceExpression reference : references) {
      if (!PsiUtil.isAccessedForWriting(reference)) continue;
      if (!(reference.getParent() instanceof PsiAssignmentExpression assign) || assign.getOperationTokenType() != JavaTokenType.EQ) {
        return List.of();
      }
      if (!(assign.getParent() instanceof PsiExpressionStatement statement)) return List.of();
      initializers.add(statement);
    }
    return initializers;
  }

  sealed interface InitMode {
    @NotNull InitMode join(@NotNull InitMode nextMode);

    int numberOfNonInitializedBranches();

    private static InitMode fromInitializer(@NotNull PsiStatement statement, @NotNull InitMode origMode) {
      if (statement.getParent() instanceof PsiCodeBlock codeBlock && codeBlock.getParent() instanceof PsiBlockStatement block) {
        statement = block;
      }
      if (statement.getParent() instanceof PsiIfStatement ifStatement) {
        if (ifStatement.getThenBranch() == statement) {
          return new Branched(ifStatement, origMode, ExactMode.NOT_INITIALIZED);
        } else {
          return new Branched(ifStatement, ExactMode.NOT_INITIALIZED, origMode);
        }
      }
      return ExactMode.BOTTOM;
    }

    static @NotNull InitMode create(@NotNull PsiExpressionStatement statement, @NotNull PsiIfStatement topStatement) {
      PsiStatement current = statement;
      InitMode curMode = ExactMode.INITIALIZED;
      while (current != topStatement) {
        curMode = fromInitializer(current, curMode);
        if (curMode instanceof Branched branched) {
          current = branched.ifStatement();
        } else {
          return ExactMode.BOTTOM;
        }
      }
      return curMode;
    }
  }

  enum ExactMode implements InitMode {
    INITIALIZED, NOT_INITIALIZED, BOTTOM;

    @Override
    public @NotNull InitMode join(@NotNull InitMode nextMode) {
      if (nextMode == NOT_INITIALIZED) return this;
      if (this == BOTTOM || this == INITIALIZED) return BOTTOM;
      return nextMode;
    }

    @Override
    public int numberOfNonInitializedBranches() {
      return this == NOT_INITIALIZED ? 1 : 0;
    }
  }

  record Branched(@NotNull PsiIfStatement ifStatement, @NotNull InitMode thenBranch, @NotNull InitMode elseBranch) implements InitMode {
    @Override
    public @NotNull InitMode join(@NotNull InitMode nextMode) {
      if (nextMode == ExactMode.NOT_INITIALIZED) return this;
      if (!(nextMode instanceof Branched branched)) return ExactMode.BOTTOM;
      if (ifStatement() != branched.ifStatement()) return ExactMode.BOTTOM;
      InitMode newThen = thenBranch().join(branched.thenBranch());
      InitMode newElse = elseBranch().join(branched.elseBranch());
      if (newThen == ExactMode.BOTTOM || newElse == ExactMode.BOTTOM) return ExactMode.BOTTOM;
      if (newThen == newElse) return newThen;
      return new Branched(ifStatement(), newThen, newElse);
    }

    @Override
    public int numberOfNonInitializedBranches() {
      return thenBranch().numberOfNonInitializedBranches() + elseBranch().numberOfNonInitializedBranches();
    }

    void addInitializer(@NotNull PsiStatement statement) {
      if (thenBranch() == ExactMode.NOT_INITIALIZED) {
        PsiStatement branch = ifStatement().getThenBranch();
        if (branch == null) {
          ifStatement().setThenBranch(JavaPsiFacade.getElementFactory(ifStatement().getProject()).createStatementFromText("{}", null));
          branch = Objects.requireNonNull(ifStatement().getThenBranch());
        }
        BlockUtils.addBefore(branch, statement);
      } else if (thenBranch() instanceof Branched branched) {
        branched.addInitializer(statement);
      }
      if (elseBranch() == ExactMode.NOT_INITIALIZED) {
        PsiStatement branch = ifStatement().getElseBranch();
        if (branch == null) {
          ifStatement().setElseBranch(JavaPsiFacade.getElementFactory(ifStatement().getProject()).createStatementFromText("{}", null));
          branch = Objects.requireNonNull(ifStatement().getElseBranch());
        }
        BlockUtils.addBefore(branch, statement);
      }
      else if (elseBranch() instanceof Branched branched) {
        branched.addInitializer(statement);
      }
    }
  }
}