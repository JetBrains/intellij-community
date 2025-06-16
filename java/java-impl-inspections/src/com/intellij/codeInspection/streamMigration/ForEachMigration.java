// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.streamMigration.CollectMigration.getAddedElementType;

final class ForEachMigration extends BaseStreamApiMigration {
  private static final Logger LOG = Logger.getInstance(ForEachMigration.class);

  ForEachMigration(boolean shouldWarn, String forEachMethodName) {
    super(shouldWarn, forEachMethodName);
  }

  static @Nullable PsiExpression tryExtractMapExpression(TerminalBlock tb) {
    PsiMethodCallExpression call = tb.getSingleMethodCall();
    if(call == null) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if(args.length != 1) return null;
    PsiExpression arg = args[0];
    if(ExpressionUtils.isReferenceTo(arg, tb.getVariable())) return null;
    if(PsiTypes.voidType().equals(arg.getType())) return null;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if(tb.dependsOn(qualifier) ||
       VariableAccessUtils.variableIsUsed(tb.getVariable(), qualifier) ||
       StreamApiMigrationInspection.isExpressionDependsOnUpdatedCollections(arg, qualifier) ||
       !LambdaCanBeMethodReferenceInspection.checkQualifier(qualifier)) return null;
    return arg;
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiStatement loopStatement = tb.getStreamSourceStatement();
    CommentTracker ct = new CommentTracker();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    PsiExpression mapExpression = tryExtractMapExpression(tb);

    if(mapExpression != null) {
      PsiMethodCallExpression call = tb.getSingleMethodCall();
      LOG.assertTrue(call != null);
      PsiType addedType = getAddedElementType(call);
      if (addedType == null) addedType = call.getType();
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      SuggestedNameInfo suggestedNameInfo =
        codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, addedType, true);
      if (suggestedNameInfo.names.length == 0) {
        suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, "item", null, null, true);
      }
      String varName = codeStyleManager.suggestUniqueVariableName(suggestedNameInfo, call, false).names[0];

      String streamText = tb.add(new StreamApiMigrationInspection.MapOp(mapExpression, tb.getVariable(), addedType)).generate(ct);
      String forEachBody = varName + "->" + ct.text(call.getMethodExpression()) + "(" + varName + ")";
      String callText = streamText + "." + getReplacement() + "(" + forEachBody + ");";
      return ct.replaceAndRestoreComments(loopStatement, callText);
    }

    tb.replaceContinueWithReturn(factory);

    String stream = tb.generate(ct, true) + "." + getReplacement() + "(";
    PsiElement block = tb.convertToElement(ct, factory);

    final String functionalExpressionText = tb.getVariable().getName() + " -> " + wrapInBlock(ct, block);
    PsiExpressionStatement callStatement =
      (PsiExpressionStatement)ct.replaceAndRestoreComments(loopStatement, stream + functionalExpressionText + ");");

    final PsiExpressionList argumentList = ((PsiCallExpression)callStatement.getExpression()).getArgumentList();
    LOG.assertTrue(argumentList != null, callStatement.getText());
    final PsiExpression[] expressions = argumentList.getExpressions();
    LOG.assertTrue(expressions.length == 1);

    if (expressions[0] instanceof PsiFunctionalExpression &&
        ((PsiFunctionalExpression)expressions[0]).getFunctionalInterfaceType() == null) {
      PsiTypeElement typeElement = tb.getVariable().getTypeElement();
      if (typeElement != null) {
        String typedVariable = typeElement.getText() + " " + tb.getVariable().getName();
        ct = new CommentTracker();
        callStatement = (PsiExpressionStatement)ct
          .replaceAndRestoreComments(callStatement, stream + "(" + typedVariable + ") -> " + wrapInBlock(ct, block) + ");");
      }
    }
    return callStatement;
  }

  private static @NotNull String wrapInBlock(@NotNull CommentTracker ct, @NotNull PsiElement block) {
    if (block instanceof PsiExpressionStatement) {
      return ct.text(((PsiExpressionStatement)block).getExpression());
    }
    if (block instanceof PsiCodeBlock) {
      return ct.text(block);
    }
    return "{" + ct.text(block) + "}";
  }
}
