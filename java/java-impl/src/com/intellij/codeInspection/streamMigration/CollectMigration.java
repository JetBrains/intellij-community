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

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.TerminalBlock;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Tagir Valeev
 */
class CollectMigration extends BaseStreamApiMigration {
  private static final Logger LOG = Logger.getInstance(CollectMigration.class);

  protected CollectMigration(String methodName) {
    super(methodName);
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
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    PsiLoopStatement loopStatement = tb.getMainLoop();
    tb = tb.tryPeelLimit();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiMethodCallExpression call = tb.getSingleMethodCall();
    if (call == null) return null;

    restoreComments(loopStatement, body);
    if (!tb.hasOperations() && StreamApiMigrationInspection.isAddAllCall(tb) && loopStatement instanceof PsiForeachStatement) {
      return handleAddAll(loopStatement, factory, call);
    }
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    PsiLocalVariable variable = StreamApiMigrationInspection.extractCollectionVariable(qualifierExpression);
    if(variable != null && InheritanceUtil.isInheritor(variable.getType(), CommonClassNames.JAVA_UTIL_MAP)) {
      PsiElement result = handleToMap(loopStatement, tb, call, variable);
      if (result != null) return result;
    }

    PsiExpression itemToAdd = call.getArgumentList().getExpressions()[0];
    PsiType addedType = getAddedElementType(call);
    if (addedType == null) addedType = itemToAdd.getType();

    if(variable == null && qualifierExpression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression qualifierCall = (PsiMethodCallExpression)qualifierExpression;
      if(StreamApiMigrationInspection.isCallOf(qualifierCall, CommonClassNames.JAVA_UTIL_MAP, "computeIfAbsent")) {
        PsiElement result = handleComputeIfAbsent(loopStatement, tb, itemToAdd, qualifierCall);
        if (result != null) return result;
      }
    }

    StringBuilder builder = generateStream(new MapOp(tb.getLastOperation(), itemToAdd, tb.getVariable(), addedType));

    if (variable != null) {
      InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(variable, loopStatement);
      if(status != InitializerUsageStatus.UNKNOWN) {
        PsiExpression initializer = variable.getInitializer();
        LOG.assertTrue(initializer != null);
        PsiElement toArrayConversion = handleToArray(builder, initializer, loopStatement, call);
        if(toArrayConversion != null) {
          if(status != InitializerUsageStatus.AT_WANTED_PLACE) {
            variable.delete();
          }
          return toArrayConversion;
        }
        PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(loopStatement, PsiComment.class, PsiWhiteSpace.class);
        String comparatorText = StreamApiMigrationInspection.tryExtractSortComparatorText(nextStatement, variable);
        if(comparatorText != null) {
          builder.append(".sorted(").append(comparatorText).append(")");
          nextStatement.delete();
        }
        String callText = builder.append(".collect(" + CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".")
          .append(createInitializerReplacementText(qualifierExpression.getType(), initializer))
          .append(")").toString();
        return replaceInitializer(loopStatement, variable, initializer, callText, status);
      }
    }
    String qualifierText = qualifierExpression != null ? qualifierExpression.getText() + "." : "";

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo =
      codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, addedType, false);
    if (suggestedNameInfo.names.length == 0) {
      suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, "item", null, null, false);
    }
    String varName = codeStyleManager.suggestUniqueVariableName(suggestedNameInfo, call, false).names[0];

    PsiExpression forEachBody =
      factory.createExpressionFromText(qualifierText + "add(" + varName + ")", qualifierExpression);
    String callText = builder.append(".forEach(").append(varName).append("->").append(forEachBody.getText()).append(");").toString();
    return loopStatement.replace(factory.createStatementFromText(callText, loopStatement));
  }

  @Nullable
  private static PsiElement handleComputeIfAbsent(@NotNull PsiLoopStatement loopStatement, @NotNull TerminalBlock tb,
                                                  PsiExpression itemToAdd, PsiMethodCallExpression qualifierCall) {
    PsiLocalVariable variable;
    variable = StreamApiMigrationInspection.extractCollectionVariable(qualifierCall.getMethodExpression().getQualifierExpression());
    if (variable == null || !InheritanceUtil.isInheritor(variable.getType(), CommonClassNames.JAVA_UTIL_MAP)) return null;
    InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(variable, loopStatement);
    if(status == InitializerUsageStatus.UNKNOWN) return null;
    PsiExpression[] computeArgs = qualifierCall.getArgumentList().getExpressions();
    if(!(computeArgs[1] instanceof PsiLambdaExpression)) return null;
    PsiExpression ctor = LambdaUtil.extractSingleExpressionFromBody(((PsiLambdaExpression)computeArgs[1]).getBody());
    PsiType mapType = variable.getType();
    PsiType valueType = PsiUtil.substituteTypeParameter(mapType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
    if(valueType == null) return null;
    String downstreamCollector = CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + "." + createInitializerReplacementText(valueType, ctor);
    if(!ExpressionUtils.isReferenceTo(itemToAdd, tb.getVariable())) {
      downstreamCollector = CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".mapping(" +
                            tb.getVariable().getName() + "->" + itemToAdd.getText() + "," + downstreamCollector + ")";
    }
    StringBuilder builder = generateStream(tb.getLastOperation());
    builder.append(".collect(" + CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".groupingBy(")
      .append(LambdaUtil.createLambda(tb.getVariable(), computeArgs[0]));
    PsiExpression initializer = variable.getInitializer();
    LOG.assertTrue(initializer != null);
    if (!isHashMap(variable)) {
      builder.append(",()->").append(initializer.getText()).append(",").append(downstreamCollector);
    }
    else if (!(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + "." + "toList()").equals(downstreamCollector)) {
      builder.append(",").append(downstreamCollector);
    }
    builder.append("))");
    return replaceInitializer(loopStatement, variable, initializer, builder.toString(), status);
  }

  @Nullable
  private static PsiElement handleAddAll(@NotNull PsiLoopStatement loopStatement, PsiElementFactory factory, PsiMethodCallExpression call) {
    PsiExpression iteratedValue = ((PsiForeachStatement)loopStatement).getIteratedValue();
    if (iteratedValue == null) return null;
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    String qualifierText = qualifierExpression != null ? qualifierExpression.getText() : "";
    String collectionText = iteratedValue.getType() instanceof PsiArrayType
                            ? CommonClassNames.JAVA_UTIL_ARRAYS + ".asList(" + iteratedValue.getText() + ")"
                            : iteratedValue.getText();
    String callText = StringUtil.getQualifiedName(qualifierText, "addAll(" + collectionText + ");");
    return loopStatement.replace(factory.createStatementFromText(callText, loopStatement));
  }

  @Nullable
  private static PsiElement handleToMap(@NotNull PsiLoopStatement loopStatement,
                                        @NotNull TerminalBlock tb,
                                        PsiMethodCallExpression call,
                                        PsiLocalVariable variable) {
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if(args.length < 2) return null;
    String methodName = call.getMethodExpression().getReferenceName();
    if(methodName == null) return null;
    InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(variable, loopStatement);
    if(status == InitializerUsageStatus.UNKNOWN) return null;
    Project project = loopStatement.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiExpression merger;
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    String aVar = codeStyleManager.suggestUniqueVariableName("a", call, true);
    String bVar = codeStyleManager.suggestUniqueVariableName("b", call, true);
    switch(methodName) {
      case "put":
        merger = factory.createExpressionFromText("("+aVar+","+bVar+")->"+bVar, call);
        break;
      case "putIfAbsent":
        merger = factory.createExpressionFromText("("+aVar+","+bVar+")->"+aVar, call);
        break;
      case "merge":
        if(args.length != 3) return null;
        merger = args[2];
        break;
      default:
        return null;
    }
    StringBuilder collector = new StringBuilder(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS+".toMap(");
    collector.append(LambdaUtil.createLambda(tb.getVariable(), args[0])).append(',')
      .append(LambdaUtil.createLambda(tb.getVariable(), args[1])).append(',')
      .append(merger.getText());
    PsiExpression initializer = variable.getInitializer();
    LOG.assertTrue(initializer != null);
    if(!isHashMap(variable)) {
      collector.append(",()->").append(initializer.getText());
    }
    collector.append(")");
    String callText = generateStream(tb.getLastOperation()).append(".collect(").append(collector).append(")").toString();
    return replaceInitializer(loopStatement, variable, initializer, callText, status);
  }

  private static boolean isHashMap(PsiLocalVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    LOG.assertTrue(initializer != null);
    PsiClass initializerClass = PsiUtil.resolveClassInClassTypeOnly(initializer.getType());
    PsiClass varClass = PsiUtil.resolveClassInClassTypeOnly(variable.getType());
    return initializerClass != null &&
           varClass != null &&
           CommonClassNames.JAVA_UTIL_HASH_MAP.equals(initializerClass.getQualifiedName()) &&
           CommonClassNames.JAVA_UTIL_MAP.equals(varClass.getQualifiedName());
  }

  @Nullable
  private static PsiElement handleToArray(StringBuilder builder,
                                          PsiExpression initializer,
                                          PsiLoopStatement loopStatement,
                                          PsiMethodCallExpression methodCallExpression) {
    PsiMethodCallExpression toArrayExpression =
      StreamApiMigrationInspection.extractToArrayExpression(loopStatement, methodCallExpression);
    if (toArrayExpression == null) return null;
    PsiType type = initializer.getType();
    if (!(type instanceof PsiClassType)) return null;
    String replacement = StreamApiMigrationInspection.COLLECTION_TO_ARRAY.get(((PsiClassType)type).rawType().getCanonicalText());
    if (replacement == null) return null;

    builder.append(".").append(replacement);
    PsiExpression[] args = toArrayExpression.getArgumentList().getExpressions();
    if(args.length == 0) {
      builder.append("()");
    } else {
      if(args.length != 1 || !(args[0] instanceof PsiNewExpression)) return null;
      PsiNewExpression newArray = (PsiNewExpression)args[0];
      PsiType arrayType = newArray.getType();
      if(arrayType == null) return null;
      String name = arrayType.getCanonicalText();
      builder.append('(').append(name).append("::new)");
    }
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(toArrayExpression.getProject());
    PsiElement result = toArrayExpression.replace(elementFactory.createExpressionFromText(builder.toString(), toArrayExpression));
    removeLoop(loopStatement);
    return result;
  }

  @NotNull
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
    else {
      return "toCollection(() -> " + initializer.getText() + ")";
    }
  }
}
