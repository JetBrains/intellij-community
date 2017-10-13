// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.util.ObjectUtils.tryCast;

public class IdempotentLoopBodyInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitWhileStatement(PsiWhileStatement loop) {
        PsiExpression condition = loop.getCondition();
        if (condition == null || SideEffectChecker.mayHaveSideEffects(condition)) return;
        if (isIdempotent(loop.getBody())) {
          holder.registerProblem(loop.getFirstChild(), InspectionsBundle.message("inspection.idempotent.loop.body"));
        }
      }

      @Override
      public void visitForStatement(PsiForStatement loop) {
        PsiExpression condition = loop.getCondition();
        if (condition == null || SideEffectChecker.mayHaveSideEffects(condition)) return;
        if (isIdempotent(loop.getBody(), loop.getUpdate())) {
          holder.registerProblem(loop.getFirstChild(), InspectionsBundle.message("inspection.idempotent.loop.body"));
        }
      }

      private boolean isIdempotent(PsiStatement... statements) {
        Set<PsiVariable> variables = extractWrites(statements);
        if (variables == null || variables.isEmpty()) return false;
        if (!(variables instanceof HashSet)) {
          variables = new HashSet<>(variables);
        }
        for (PsiStatement statement : statements) {
          if (usesInputVariable(statement, variables)) {
            return false;
          }
        }
        return true;
      }

      private boolean usesInputVariable(PsiStatement statement, Set<PsiVariable> variables) {
        if (statement == null) return false;
        if (statement instanceof PsiBlockStatement) {
          for (PsiStatement st : ((PsiBlockStatement)statement).getCodeBlock().getStatements()) {
            if (usesInputVariable(st, variables)) {
              return true;
            }
          }
          return false;
        }
        if (statement instanceof PsiExpressionStatement) {
          PsiAssignmentExpression assignment = tryCast(((PsiExpressionStatement)statement).getExpression(), PsiAssignmentExpression.class);
          if (assignment != null) {
            if (anyVariableIsUsed(assignment.getRExpression(), variables)) return true;
            PsiReferenceExpression ref =
              tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()), PsiReferenceExpression.class);
            if (ref != null) {
              PsiElement var = ref.resolve();
              if (var instanceof PsiVariable) {
                variables.remove(var);
              }
            }
            return false;
          }
        }
        if (statement instanceof PsiIfStatement) {
          PsiIfStatement ifStatement = (PsiIfStatement)statement;
          if (anyVariableIsUsed(ifStatement.getCondition(), variables)) return true;
          Set<PsiVariable> thenVars = new HashSet<>(variables);
          if (usesInputVariable(ifStatement.getThenBranch(), thenVars)) return true;
          Set<PsiVariable> elseVars = new HashSet<>(variables);
          if (usesInputVariable(ifStatement.getElseBranch(), elseVars)) return true;
          thenVars.addAll(elseVars);
          variables.retainAll(thenVars);
          return false;
        }
        if (statement instanceof PsiDeclarationStatement) {
          StreamEx.of(((PsiDeclarationStatement)statement).getDeclaredElements()).select(PsiVariable.class)
            .forEach(variables::remove);
        }
        return anyVariableIsUsed(statement, variables);
      }

      private boolean anyVariableIsUsed(@Nullable PsiElement statement, @NotNull Set<PsiVariable> variables) {
        return VariableAccessUtils.collectUsedVariables(statement).stream().anyMatch(variables::contains);
      }

      /**
       * Extract written variables from statement which may affect the next iteration
       * @param statement
       * @return list of written variables or null if the statement may have unknown side effects, thus further analysis is impossible.
       */
      @Nullable
      private Set<PsiVariable> extractWrites(@Nullable PsiStatement statement) {
        if (statement == null ||
            statement instanceof PsiEmptyStatement ||
            (statement instanceof PsiContinueStatement && ((PsiContinueStatement)statement).getLabelIdentifier() == null)) {
          return Collections.emptySet();
        }
        if (statement instanceof PsiBlockStatement) {
          PsiStatement[] statements = ((PsiBlockStatement)statement).getCodeBlock().getStatements();
          return extractWrites(statements);
        }
        if (statement instanceof PsiDeclarationStatement) {
          PsiElement[] elements = ((PsiDeclarationStatement)statement).getDeclaredElements();
          for (PsiElement element : elements) {
            if (!(element instanceof PsiLocalVariable)) return null;
            PsiLocalVariable var = (PsiLocalVariable)element;
            PsiExpression initializer = var.getInitializer();
            if (initializer != null && SideEffectChecker.mayHaveSideEffects(initializer)) return null;
          }
          return Collections.emptySet();
        }
        if (statement instanceof PsiExpressionStatement) {
          PsiAssignmentExpression assignment = tryCast(((PsiExpressionStatement)statement).getExpression(), PsiAssignmentExpression.class);
          if (assignment == null ||
              assignment.getOperationTokenType() != JavaTokenType.EQ ||
              assignment.getRExpression() == null ||
              SideEffectChecker.mayHaveSideEffects(assignment.getRExpression())) {
            return null;
          }
          PsiReferenceExpression ref =
            tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()), PsiReferenceExpression.class);
          if (ref == null) return null;
          PsiElement var = ref.resolve();
          if (var instanceof PsiLocalVariable || var instanceof PsiParameter) {
            return Collections.singleton((PsiVariable)var);
          }
        }
        if (statement instanceof PsiIfStatement) {
          PsiIfStatement ifStatement = (PsiIfStatement)statement;
          PsiExpression condition = ifStatement.getCondition();
          if (condition == null || SideEffectChecker.mayHaveSideEffects(condition)) return null;
          Set<PsiVariable> thenResult = extractWrites(ifStatement.getThenBranch());
          if (thenResult == null) return null;
          Set<PsiVariable> elseResult = extractWrites(ifStatement.getElseBranch());
          if (elseResult == null) return null;
          if (thenResult.isEmpty()) return elseResult;
          if (elseResult.isEmpty()) return thenResult;
          return StreamEx.of(thenResult, elseResult).toFlatCollection(Function.identity(), HashSet::new);
        }
        return null;
      }

      @Nullable
      private Set<PsiVariable> extractWrites(PsiStatement... statements) {
        Set<PsiVariable> result = new HashSet<>();
        for (PsiStatement subStatement : statements) {
          Set<PsiVariable> subResult = extractWrites(subStatement);
          if (subResult == null) return null;
          result.addAll(subResult);
        }
        return result;
      }
    };
  }
}
