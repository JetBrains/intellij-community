// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.IteratorDeclaration;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        PsiStatement body = statement.getBody();
        if(!(body instanceof PsiBlockStatement)) return;
        PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
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
        //noinspection DialogTitleCapitalization
        holder.registerProblem(statement, new TextRange(0, endToken.getTextOffset() - statement.getTextOffset() + 1),
                               QuickFixBundle.message("java.8.collection.removeif.inspection.description"),
                               new ReplaceWithRemoveIfQuickFix());
      }

      @Nullable
      private PsiExpression checkAndExtractCondition(IteratorDeclaration declaration,
                                                     PsiIfStatement ifStatement) {
        PsiExpression condition = ifStatement.getCondition();
        if (condition == null || ifStatement.getElseBranch() != null) return null;
        PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
        if (!(thenStatement instanceof PsiExpressionStatement)) return null;
        if (!declaration.isIteratorMethodCall(((PsiExpressionStatement)thenStatement).getExpression(), "remove")) return null;
        if (!LambdaGenerationUtil.canBeUncheckedLambda(condition)) return null;
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
      PsiElement element = descriptor.getStartElement();
      if(!(element instanceof PsiLoopStatement)) return;
      PsiLoopStatement loop = (PsiLoopStatement)element;
      IteratorDeclaration declaration;
      declaration = IteratorDeclaration.fromLoop(loop);
      if(declaration == null) return;
      PsiStatement body = loop.getBody();
      if(!(body instanceof PsiBlockStatement)) return;
      PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      String replacement = null;
      CommentTracker ct = new CommentTracker();
      if (statements.length == 2 && statements[1] instanceof PsiIfStatement) {
        PsiVariable variable = declaration.getNextElementVariable(statements[0]);
        if (variable == null) return;
        PsiExpression condition = ((PsiIfStatement)statements[1]).getCondition();
        if (condition == null) return;
        replacement = generateRemoveIf(declaration, ct, condition, variable.getName());
      }
      else if (statements.length == 1 && statements[0] instanceof PsiIfStatement){
        PsiExpression condition = ((PsiIfStatement)statements[0]).getCondition();
        if (condition == null) return;
        PsiElement ref = declaration.findOnlyIteratorRef(condition);
        if(ref != null) {
          PsiElement call = ref.getParent().getParent();
          if(!declaration.isIteratorMethodCall(call, "next")) return;
          PsiType type = ((PsiExpression)call).getType();
          JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
          SuggestedNameInfo info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type);
          if(info.names.length == 0) {
            info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, "value", null, type);
          }
          String paramName = javaCodeStyleManager.suggestUniqueVariableName(info, condition, true).names[0];
          ct.replace(call, factory.createIdentifier(paramName));
          replacement = generateRemoveIf(declaration, ct, condition, paramName);
        }
      }
      if (replacement == null) return;
      ct.delete(declaration.getIterator());
      PsiElement result = ct.replaceAndRestoreComments(loop, replacement);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }

    @NotNull
    private static String generateRemoveIf(IteratorDeclaration declaration, CommentTracker ct,
                                           PsiExpression condition, String paramName) {
      return (declaration.getIterable() == null ? "" : ct.text(declaration.getIterable()) + ".") +
             "removeIf(" + paramName + "->" + ct.text(condition) + ");";
    }
  }
}