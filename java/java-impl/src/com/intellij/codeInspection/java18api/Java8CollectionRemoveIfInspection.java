// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.ForEachCollectionTraversal;
import com.intellij.codeInspection.util.IterableTraversal;
import com.intellij.codeInspection.util.IteratorDeclaration;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
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

import static com.intellij.util.ObjectUtils.tryCast;

public class Java8CollectionRemoveIfInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!JavaFeature.ADVANCED_COLLECTIONS_API.isFeatureSupported(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      void handleIteratorLoop(PsiLoopStatement statement, PsiJavaToken endToken, IteratorDeclaration declaration) {
        if (endToken == null || declaration == null || !declaration.isCollection()) return;
        PsiStatement[] statements = ControlFlowUtils.unwrapBlock(statement.getBody());
        if (statements.length == 2 && statements[1] instanceof PsiIfStatement) {
          PsiVariable element = declaration.getNextElementVariable(statements[0]);
          if (element == null) return;
          PsiIfStatement ifStatement = (PsiIfStatement)statements[1];
          if(checkAndExtractCondition(declaration, ifStatement) == null) return;
          registerProblem(statement, endToken);
        }
        else if (statements.length == 1 && statements[0] instanceof PsiIfStatement){
          PsiIfStatement ifStatement = (PsiIfStatement)statements[0];
          PsiExpression condition = checkAndExtractCondition(declaration, ifStatement);
          if (condition == null) return;
          PsiElement ref = declaration.findOnlyIteratorRef(condition);
          if (ref != null && declaration.isIteratorMethodCall(ref.getParent().getParent(), "next") && isAlwaysExecuted(condition, ref)) {
            registerProblem(statement, endToken);
          }
        }
      }

      private boolean isAlwaysExecuted(PsiExpression condition, PsiElement ref) {
        while(ref != condition) {
          PsiElement parent = ref.getParent();
          if(parent instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
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

      private void registerProblem(PsiLoopStatement statement, PsiJavaToken endToken) {
        holder.registerProblem(statement.getFirstChild(),
                               QuickFixBundle.message("java.8.collection.removeif.inspection.description"),
                               new ReplaceWithRemoveIfQuickFix());
      }

      @Nullable
      private PsiExpression checkAndExtractCondition(IterableTraversal traversal, PsiIfStatement ifStatement) {
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
      public void visitForStatement(PsiForStatement statement) {
        super.visitForStatement(statement);
        IteratorDeclaration declaration = IteratorDeclaration.fromLoop(statement);
        handleIteratorLoop(statement, statement.getRParenth(), declaration);
      }

      @Override
      public void visitWhileStatement(PsiWhileStatement statement) {
        super.visitWhileStatement(statement);
        IteratorDeclaration declaration = IteratorDeclaration.fromLoop(statement);
        handleIteratorLoop(statement, statement.getRParenth(), declaration);
      }

      @Override
      public void visitForeachStatement(PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        ForEachCollectionTraversal traversal = ForEachCollectionTraversal.fromLoop(statement);
        if (traversal == null) return;
        PsiIfStatement ifStatement = tryCast(ControlFlowUtils.stripBraces(statement.getBody()), PsiIfStatement.class);
        if (ifStatement == null) return;
        PsiExpression condition = checkAndExtractCondition(traversal, ifStatement);
        if (condition == null) return;
        PsiJavaToken endToken = statement.getRParenth();
        if (endToken == null) return;
        registerProblem(statement, endToken);
      }
    };
  }

  private static class ReplaceWithRemoveIfQuickFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("java.8.collection.removeif.inspection.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement().getParent();
      if(!(element instanceof PsiLoopStatement)) return;
      PsiLoopStatement loop = (PsiLoopStatement)element;
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
          case 1:
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
            break;
          case 2:
            PsiVariable variable = declaration.getNextElementVariable(statements[0]);
            if (variable == null) return;
            replacement = generateRemoveIf(declaration, ct, condition, variable.getName());
            break;
          default:
            return;
        }
        ct.delete(declaration.getIterator());
      }
      PsiElement result = ct.replaceAndRestoreComments(loop, replacement);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }

    @NotNull
    private static String generateRemoveIf(IterableTraversal traversal, CommentTracker ct,
                                           PsiExpression condition, String paramName) {
      return (traversal.getIterable() == null ? "" : ct.text(traversal.getIterable()) + ".") +
             "removeIf(" + paramName + "->" + ct.text(condition) + ");";
    }
  }
}