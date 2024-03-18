// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix.makefinal;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class MoveInitializerToIfBranchFixer implements EffectivelyFinalFixer {
  @Override
  public boolean isAvailable(@NotNull PsiLocalVariable var) {
    PsiExpression initializer = var.getInitializer();
    Branched branched = extractInitMode(var);
    // Do not add too many branches
    return branched != null && branched.numberOfNonInitializedBranches() <= 3 &&
           canReorder(initializer, branched);
  }

  @Override
  public void fix(@NotNull PsiLocalVariable var) {
    PsiExpression initializer = Objects.requireNonNull(var.getInitializer());
    Branched branched = Objects.requireNonNull(extractInitMode(var));
    PsiStatement statement = JavaPsiFacade.getElementFactory(var.getProject())
      .createStatementFromText(var.getName() + "=" + initializer.getText() + ";", null);
    branched.addInitializer(statement);
    initializer.delete();
  }

  @Override
  public String getText(@NotNull PsiLocalVariable var) {
    return JavaBundle.message("intention.make.final.fixer.if", var.getName());
  }

  private static boolean canReorder(PsiExpression initializer, Branched branched) {
    if (ExpressionUtils.isSafelyRecomputableExpression(initializer) && !refersToNonFinalLocal(initializer)) return true;
    if (SideEffectChecker.mayHaveSideEffects(initializer)) return false;
    PsiIfStatement ifStatement = branched.ifStatement();
    PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(initializer, PsiDeclarationStatement.class);
    if (declaration == null || declaration.getParent() != ifStatement.getParent()) return false;
    PsiElement[] elements = declaration.getDeclaredElements();
    if (elements.length > 1) {
      int i = ContainerUtil.indexOf(Arrays.asList(elements), e -> PsiTreeUtil.isAncestor(e, initializer, true));
      for (int j = i + 1; j < elements.length; j++) {
        if (elements[j] instanceof PsiVariable var) {
          PsiExpression nextInitializer = var.getInitializer();
          if (nextInitializer != null && SideEffectChecker.mayHaveSideEffects(nextInitializer)) return false;
        }
      }
    }
    PsiStatement nextStatement = declaration;
    while (true) {
      nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      if (nextStatement == null) return false;
      if (nextStatement == ifStatement) break;
      if (nextStatement instanceof PsiDeclarationStatement decl) {
        if (StreamEx.of(decl.getDeclaredElements()).select(PsiLocalVariable.class).map(PsiLocalVariable::getInitializer).nonNull()
          .anyMatch(SideEffectChecker::mayHaveSideEffects)) {
          return false;
        }
        continue;
      }
      return false;
    }
    return branched.conditions().noneMatch(c -> SideEffectChecker.mayHaveSideEffects(c));
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
    List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(var);
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

    default StreamEx<PsiExpression> conditions() {
      return StreamEx.empty();
    }

    private static InitMode fromInitializer(@NotNull PsiStatement statement, @NotNull InitMode origMode) {
      if (statement.getParent() instanceof PsiCodeBlock codeBlock && codeBlock.getParent() instanceof PsiBlockStatement block) {
        statement = block;
      }
      if (statement.getParent() instanceof PsiIfStatement ifStatement) {
        if (ifStatement.getThenBranch() == statement) {
          return new Branched(ifStatement, origMode, ExactMode.NOT_INITIALIZED);
        }
        else {
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
        }
        else {
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
    public StreamEx<PsiExpression> conditions() {
      return StreamEx.ofNullable(ifStatement.getCondition()).append(thenBranch().conditions())
        .append(elseBranch().conditions());
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
      }
      else if (thenBranch() instanceof Branched branched) {
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
