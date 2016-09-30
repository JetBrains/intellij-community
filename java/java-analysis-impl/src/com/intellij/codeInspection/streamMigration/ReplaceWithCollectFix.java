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
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.Operation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Tagir Valeev
 */
class ReplaceWithCollectFix extends MigrateToStreamFix {
  private static final Logger LOG = Logger.getInstance(ReplaceWithCollectFix.class);

  final String myMethodName;

  protected ReplaceWithCollectFix(String methodName) {
    myMethodName = methodName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with " + myMethodName;
  }

  @Nullable
  PsiType getAddedElementType(PsiMethodCallExpression call) {
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiMethod method = call.resolveMethod();
    if(method == null) return null;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if(parameters.length != 1) return null;
    return resolveResult.getSubstitutor().substitute(parameters[0].getType());
  }

  @Override
  void migrate(@NotNull Project project,
               @NotNull ProblemDescriptor descriptor,
               @NotNull PsiForeachStatement foreachStatement,
               @NotNull PsiExpression iteratedValue,
               @NotNull PsiStatement body,
               @NotNull StreamApiMigrationInspection.TerminalBlock tb,
               @NotNull List<Operation> operations) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiType iteratedValueType = iteratedValue.getType();
    final PsiMethodCallExpression methodCallExpression = tb.getSingleMethodCall();

    if (methodCallExpression == null) return;

    restoreComments(foreachStatement, body);
    if (operations.isEmpty() && StreamApiMigrationInspection.isAddAllCall(tb)) {
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
    PsiType addedType = getAddedElementType(methodCallExpression);
    if (addedType == null) addedType = itemToAdd.getType();
    operations.add(new StreamApiMigrationInspection.MapOp(itemToAdd, tb.getVariable(), addedType));
    final StringBuilder builder = generateStream(iteratedValue, operations);

    final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    final PsiLocalVariable variable = StreamApiMigrationInspection.extractCollectionVariable(qualifierExpression);
    if (variable != null) {
      InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(variable, foreachStatement);
      if(status != InitializerUsageStatus.UNKNOWN) {
        PsiExpression initializer = variable.getInitializer();
        LOG.assertTrue(initializer != null);
        PsiMethodCallExpression toArrayExpression =
          StreamApiMigrationInspection.extractToArrayExpression(foreachStatement, methodCallExpression);
        if(toArrayExpression != null) {
          PsiType type = initializer.getType();
          if(type instanceof PsiClassType) {
            String replacement = StreamApiMigrationInspection.COLLECTION_TO_ARRAY.get(((PsiClassType)type).rawType().getCanonicalText());
            if(replacement != null) {
              builder.append(".").append(replacement);
              PsiExpression[] args = toArrayExpression.getArgumentList().getExpressions();
              if(args.length == 0) {
                builder.append("()");
              } else {
                if(args.length != 1 || !(args[0] instanceof PsiNewExpression)) return;
                PsiNewExpression newArray = (PsiNewExpression)args[0];
                PsiType arrayType = newArray.getType();
                if(arrayType == null) return;
                String name = arrayType.getCanonicalText();
                builder.append('(').append(name).append("::new)");
              }
              PsiElement result =
                toArrayExpression.replace(elementFactory.createExpressionFromText(builder.toString(), toArrayExpression));
              removeLoop(foreachStatement);
              if(status != InitializerUsageStatus.AT_WANTED_PLACE) {
                variable.delete();
              }
              simplifyAndFormat(project, result);
              return;
            }
          }
        }
        String callText = builder.append(".collect(java.util.stream.Collectors.")
          .append(createInitializerReplacementText(qualifierExpression.getType(), initializer))
          .append(")").toString();
        replaceInitializer(foreachStatement, variable, initializer, callText, status);
        return;
      }
    }
    final String qualifierText = qualifierExpression != null ? qualifierExpression.getText() + "." : "";

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo =
      codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, addedType, false);
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
}
