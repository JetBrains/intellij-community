/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.streamMigration.CollectMigration.getAddedElementType;

/**
 * @author Tagir Valeev
 */
class ForEachMigration extends BaseStreamApiMigration {
  private static final Logger LOG = Logger.getInstance(ForEachMigration.class);

  protected ForEachMigration(boolean shouldWarn, String forEachMethodName) {
    super(shouldWarn, forEachMethodName);
  }

  @Nullable
  static PsiExpression tryExtractMapExpression(TerminalBlock tb) {
    PsiMethodCallExpression call = tb.getSingleMethodCall();
    if(call == null) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if(args.length != 1) return null;
    PsiExpression arg = args[0];
    if(ExpressionUtils.isReferenceTo(arg, tb.getVariable())) return null;
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
    restoreComments(loopStatement, body);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    PsiExpression mapExpression = tryExtractMapExpression(tb);

    if(mapExpression != null) {
      PsiMethodCallExpression call = tb.getSingleMethodCall();
      LOG.assertTrue(call != null);
      PsiType addedType = getAddedElementType(call);
      if (addedType == null) addedType = call.getType();
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      SuggestedNameInfo suggestedNameInfo =
        codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, addedType, false);
      if (suggestedNameInfo.names.length == 0) {
        suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, "item", null, null, false);
      }
      String varName = codeStyleManager.suggestUniqueVariableName(suggestedNameInfo, call, false).names[0];

      String streamText = tb.add(new StreamApiMigrationInspection.MapOp(mapExpression, tb.getVariable(), addedType)).generate();
      String forEachBody = varName + "->" + call.getMethodExpression().getText() + "(" + varName + ")";
      String callText = streamText + "." + getReplacement() + "(" + forEachBody + ");";
      return loopStatement.replace(factory.createStatementFromText(callText, loopStatement));
    }

    tb.replaceContinueWithReturn(factory);

    String stream = tb.generate(true) + "." + getReplacement() + "(";
    PsiElement block = tb.convertToElement(factory);

    final String functionalExpressionText = tb.getVariable().getName() + " -> " + wrapInBlock(block);
    PsiExpressionStatement callStatement = (PsiExpressionStatement)factory
      .createStatementFromText(stream + functionalExpressionText + ");", loopStatement);
    callStatement = (PsiExpressionStatement)loopStatement.replace(callStatement);

    final PsiExpressionList argumentList = ((PsiCallExpression)callStatement.getExpression()).getArgumentList();
    LOG.assertTrue(argumentList != null, callStatement.getText());
    final PsiExpression[] expressions = argumentList.getExpressions();
    LOG.assertTrue(expressions.length == 1);

    if (expressions[0] instanceof PsiFunctionalExpression &&
        ((PsiFunctionalExpression)expressions[0]).getFunctionalInterfaceType() == null) {
      PsiTypeElement typeElement = tb.getVariable().getTypeElement();
      if (typeElement != null) {
        String typedVariable = typeElement.getText() + " " + tb.getVariable().getName();
        callStatement = (PsiExpressionStatement)callStatement.replace(factory.createStatementFromText(
          stream + "(" + typedVariable + ") -> " + wrapInBlock(block) + ");", callStatement));
      }
    }
    return callStatement;
  }

  @Contract("null -> !null")
  private static String wrapInBlock(PsiElement block) {
    if (block instanceof PsiExpressionStatement) {
      return ((PsiExpressionStatement)block).getExpression().getText();
    }
    if (block instanceof PsiCodeBlock) {
      return block.getText();
    }
    return "{" + block.getText() + "}";
  }
}
