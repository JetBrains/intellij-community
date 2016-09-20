/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Tagir Valeev
 */
class ReplaceWithCollectFix extends MigrateToStreamFix {
  final String myMethodName;

  protected ReplaceWithCollectFix(String methodName) {
    myMethodName = methodName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with " + myMethodName;
  }

  @Override
  void migrate(@NotNull Project project,
               @NotNull ProblemDescriptor descriptor,
               @NotNull PsiForeachStatement foreachStatement,
               @NotNull PsiExpression iteratedValue,
               @NotNull PsiStatement body,
               @NotNull StreamApiMigrationInspection.TerminalBlock tb,
               @NotNull List<String> intermediateOps) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiType iteratedValueType = iteratedValue.getType();
    final PsiMethodCallExpression methodCallExpression = tb.getSingleMethodCall();

    if (methodCallExpression == null) return;

    restoreComments(foreachStatement, body);
    if (intermediateOps.isEmpty() && StreamApiMigrationInspection.isAddAllCall(tb)) {
      final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
      final String qualifierText = qualifierExpression != null ? qualifierExpression.getText() : "";
      final String collectionText =
        iteratedValueType instanceof PsiArrayType ? "java.util.Arrays.asList(" + iteratedValue.getText() + ")" :
        getIteratedValueText(iteratedValue);
      final String callText = StringUtil.getQualifiedName(qualifierText, "addAll(" + collectionText + ");");
      PsiElement result = foreachStatement.replace(elementFactory.createStatementFromText(callText, foreachStatement));
      simplifyAndFormat(project, result);
      return;
    }
    PsiExpression itemToAdd = methodCallExpression.getArgumentList().getExpressions()[0];
    intermediateOps.add(createMapperFunctionalExpressionText(tb.getVariable(), itemToAdd));
    final StringBuilder builder = generateStream(iteratedValue, intermediateOps);

    final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    final PsiExpression initializer = StreamApiMigrationInspection
      .extractReplaceableCollectionInitializer(qualifierExpression, foreachStatement);
    if (initializer != null) {
      String callText = builder.append(".collect(java.util.stream.Collectors.")
        .append(createInitializerReplacementText(qualifierExpression.getType(), initializer))
        .append(")").toString();
      PsiElement result = initializer.replace(elementFactory.createExpressionFromText(callText, null));
      simplifyAndFormat(project, result);
      removeLoop(foreachStatement);
      return;
    }
    final String qualifierText = qualifierExpression != null ? qualifierExpression.getText() + "." : "";

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo =
      codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, itemToAdd.getType(), false);
    if (suggestedNameInfo.names.length == 0) {
      suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, "item", null, itemToAdd.getType(), false);
    }
    String varName = codeStyleManager.suggestUniqueVariableName(suggestedNameInfo, methodCallExpression, false).names[0];

    PsiExpression forEachBody =
      elementFactory.createExpressionFromText(qualifierText + "add(" + varName + ")", qualifierExpression);
    final String callText =
      builder.append(".forEach(").append(varName).append("->").append(forEachBody.getText()).append(");").toString();
    PsiElement result = foreachStatement.replace(elementFactory.createStatementFromText(callText, foreachStatement));
    simplifyAndFormat(project, result);
  }

  private static String createInitializerReplacementText(PsiType varType, PsiExpression initializer) {
    final PsiType initializerType = initializer.getType();
    final PsiClassType rawType = initializerType instanceof PsiClassType ? ((PsiClassType)initializerType).rawType() : null;
    final PsiClassType rawVarType = varType instanceof PsiClassType ? ((PsiClassType)varType).rawType() : null;
    if (rawType != null && rawVarType != null &&
        rawType.equalsToText(CommonClassNames.JAVA_UTIL_ARRAY_LIST) &&
        (rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_LIST) || rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_COLLECTION))) {
      return "toList()";
    }
    else if (rawType != null && rawVarType != null &&
             rawType.equalsToText(CommonClassNames.JAVA_UTIL_HASH_SET) &&
             (rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_SET) ||
              rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_COLLECTION))) {
      return "toSet()";
    }
    else if (rawType != null) {
      return "toCollection(" + rawType.getClassName() + "::new)";
    }
    else {
      return "toCollection(() -> " + initializer.getText() + ")";
    }
  }

  private static String createMapperFunctionalExpressionText(PsiVariable variable, PsiExpression expression) {
    if (!StreamApiMigrationInspection.isIdentityMapping(variable, expression)) {
      return new StreamApiMigrationInspection.MapOp(expression, variable).createReplacement(null);
    }
    return "";
  }
}
