// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.ForEachCollectionTraversal;
import com.intellij.codeInspection.util.IterableTraversal;
import com.intellij.codeInspection.util.IteratorDeclaration;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

public class Java8CollectionRemoveIfInspection extends AbstractBaseJavaLocalInspectionTool {
    @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ADVANCED_COLLECTIONS_API);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      void handleIteratorLoop(PsiLoopStatement statement, PsiJavaToken endToken, IteratorDeclaration declaration) {
        if (endToken == null || declaration == null || !declaration.isCollection()) return;
        PsiStatement[] statements = ControlFlowUtils.unwrapBlock(statement.getBody());
        if (statements.length == 2 && statements[1] instanceof PsiIfStatement ifStatement) {
          PsiVariable element = declaration.getNextElementVariable(statements[0]);
          if (element == null) return;
          if(checkAndExtractCondition(declaration, ifStatement) == null) return;
          registerProblem(statement);
        }
        else if (statements.length == 1 && statements[0] instanceof PsiIfStatement ifStatement){
          PsiExpression condition = checkAndExtractCondition(declaration, ifStatement);
          if (condition == null) return;
          PsiElement ref = declaration.findOnlyIteratorRef(condition);
          if (ref != null && declaration.isIteratorMethodCall(ref.getParent().getParent(), "next") && isAlwaysExecuted(condition, ref)) {
            registerProblem(statement);
          }
        }
      }

      private static boolean isAlwaysExecuted(PsiExpression condition, PsiElement ref) {
        while(ref != condition) {
          PsiElement parent = ref.getParent();
          if(parent instanceof PsiPolyadicExpression polyadicExpression) {
            IElementType type = polyadicExpression.getOperationTokenType();
            if ((type.equals(JavaTokenType.ANDAND) || type.equals(JavaTokenType.OROR)) && polyadicExpression.getOperands()[0] != ref) {
              return false;
            }
          }
          if(parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != ref) {
            return false;
          }
          ref = parent;
        }
        return true;
      }

      private void registerProblem(PsiLoopStatement statement) {
        holder.registerProblem(statement.getFirstChild(),
                               QuickFixBundle.message("java.8.collection.removeif.inspection.description"),
                               new ReplaceWithRemoveIfQuickFix());
      }

      private static @Nullable PsiExpression checkAndExtractCondition(IterableTraversal traversal, PsiIfStatement ifStatement) {
        PsiExpression condition = ifStatement.getCondition();
        if (condition == null || ifStatement.getElseBranch() != null) return null;
        PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
        if (!(thenStatement instanceof PsiExpressionStatement)) return null;
        if (!traversal.isRemoveCall(((PsiExpressionStatement)thenStatement).getExpression())) return null;
        if (!LambdaGenerationUtil.canBeUncheckedLambda(condition)) return null;
        PsiReferenceExpression iterable = tryCast(PsiUtil.skipParenthesizedExprDown(traversal.getIterable()), PsiReferenceExpression.class);
        PsiVariable iterableVariable = iterable != null ? tryCast(iterable.resolve(), PsiVariable.class) : null;
        if (iterableVariable != null && VariableAccessUtils.variableIsUsed(iterableVariable, condition)) return null;
        return condition;
      }

      @Override
      public void visitForStatement(@NotNull PsiForStatement statement) {
        super.visitForStatement(statement);
        IteratorDeclaration declaration = IteratorDeclaration.fromLoop(statement);
        handleIteratorLoop(statement, statement.getRParenth(), declaration);
      }

      @Override
      public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
        super.visitWhileStatement(statement);
        IteratorDeclaration declaration = IteratorDeclaration.fromLoop(statement);
        handleIteratorLoop(statement, statement.getRParenth(), declaration);
      }

      @Override
      public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        ForEachCollectionTraversal traversal = ForEachCollectionTraversal.fromLoop(statement);
        if (traversal == null) return;
        PsiIfStatement ifStatement = tryCast(ControlFlowUtils.stripBraces(statement.getBody()), PsiIfStatement.class);
        if (ifStatement == null) return;
        PsiExpression condition = checkAndExtractCondition(traversal, ifStatement);
        if (condition == null) return;
        PsiJavaToken endToken = statement.getRParenth();
        if (endToken == null) return;
        registerProblem(statement);
      }
    };
  }

  private static class ReplaceWithRemoveIfQuickFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return QuickFixBundle.message("java.8.collection.removeif.inspection.fix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if(!(element.getParent() instanceof PsiLoopStatement loop)) return;
      PsiStatement[] statements = ControlFlowUtils.unwrapBlock(loop.getBody());
      PsiIfStatement ifStatement = tryCast(ArrayUtil.getLastElement(statements), PsiIfStatement.class);
      if (ifStatement == null) return;
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return;
      String replacement;
      CommentTracker ct = new CommentTracker();
      if (loop instanceof PsiForeachStatement) {
        ForEachCollectionTraversal traversal = ForEachCollectionTraversal.fromLoop((PsiForeachStatement)loop);
        if (traversal == null || statements.length != 1) return;
        replacement = generateRemoveIf(traversal, ct, condition, traversal.getParameter().getName());
      }
      else {
        IteratorDeclaration declaration = IteratorDeclaration.fromLoop(loop);
        if (declaration == null) return;
        switch (statements.length) {
          case 1 -> {
            PsiElement ref = declaration.findOnlyIteratorRef(condition);
            if (ref == null) return;
            PsiElement call = ref.getParent().getParent();
            if (!declaration.isIteratorMethodCall(call, "next")) return;
            PsiType type = ((PsiExpression)call).getType();
            JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
            SuggestedNameInfo info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type);
            if (info.names.length == 0) {
              info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, "value", null, type);
            }
            String paramName = javaCodeStyleManager.suggestUniqueVariableName(info, condition, true).names[0];
            ct.replace(call, JavaPsiFacade.getElementFactory(project).createIdentifier(paramName));
            replacement = generateRemoveIf(declaration, ct, condition, paramName);
          }
          case 2 -> {
            PsiVariable variable = declaration.getNextElementVariable(statements[0]);
            if (variable == null) return;
            replacement = generateRemoveIf(declaration, ct, condition, variable.getName());
          }
          default -> {
            return;
          }
        }
        ct.delete(declaration.getIterator());
      }
      PsiElement result = ct.replaceAndRestoreComments(loop, replacement);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }

    private static @NotNull String generateRemoveIf(IterableTraversal traversal, CommentTracker ct,
                                                    PsiExpression condition, String paramName) {
      return (traversal.getIterable() == null ? "" : ct.text(traversal.getIterable()) + ".") +
             "removeIf(" + paramName + "->" + ct.text(condition) + ");";
    }
  }
}