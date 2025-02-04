// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ControlFlowChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  ControlFlowChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkMissingReturn(@NotNull PsiCodeBlock codeBlock) {
    PsiElement gParent = codeBlock.getParent();
    PsiType returnType;
    if (gParent instanceof PsiMethod method) {
      returnType = method.getReturnType();
    }
    else if (gParent instanceof PsiLambdaExpression lambdaExpression) {
      returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression);
    }
    else {
      return;
    }
    if (returnType == null || PsiTypes.voidType().equals(returnType.getDeepComponentType())) return;
    if (!(codeBlock.getParent() instanceof PsiParameterListOwner owner)) return;

    // do not compute constant expressions for if() statement condition
    // see JLS 14.20 Unreachable Statements
    try {
      ControlFlow controlFlow = ControlFlowFactory.getControlFlowNoConstantEvaluate(codeBlock);
      if (!ControlFlowUtil.returnPresent(controlFlow)) {
        PsiJavaToken rBrace = codeBlock.getRBrace();
        PsiElement context = rBrace == null ? codeBlock.getLastChild() : rBrace;
        myVisitor.report(JavaErrorKinds.RETURN_MISSING.create(context, owner));
      }
    }
    catch (AnalysisCanceledException ignored) { }
  }

  void checkUnreachableStatement(@Nullable PsiCodeBlock codeBlock) {
    if (codeBlock == null) return;
    // do not compute constant expressions for if() statement condition
    // see JLS 14.20 Unreachable Statements
    try {
      AllVariablesControlFlowPolicy policy = AllVariablesControlFlowPolicy.getInstance();
      ControlFlow controlFlow = ControlFlowFactory.getControlFlow(codeBlock, policy, ControlFlowOptions.NO_CONST_EVALUATE);
      PsiElement unreachableStatement = ControlFlowUtil.getUnreachableStatement(controlFlow);
      if (unreachableStatement != null) {
        if (unreachableStatement instanceof PsiCodeBlock && unreachableStatement.getParent() instanceof PsiBlockStatement) {
          unreachableStatement = unreachableStatement.getParent();
        }
        if (unreachableStatement instanceof PsiStatement) {
          PsiElement parent = unreachableStatement.getParent();
          if (parent instanceof PsiWhileStatement || parent instanceof PsiForStatement) {
            PsiExpression condition = ((PsiConditionalLoopStatement)parent).getCondition();
            PsiConstantEvaluationHelper evaluator = JavaPsiFacade.getInstance(myVisitor.project()).getConstantEvaluationHelper();
            if (Boolean.FALSE.equals(evaluator.computeConstantExpression(condition))) {
              myVisitor.report(JavaErrorKinds.STATEMENT_UNREACHABLE_LOOP_BODY.create(condition));
              return;
            }
          }
        }
        myVisitor.report(JavaErrorKinds.STATEMENT_UNREACHABLE.create(unreachableStatement));
      }
    }
    catch (AnalysisCanceledException | IndexNotReadyException e) {
      // incomplete code
    }
  }
}
