// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesRecorder;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;

import java.util.stream.IntStream;

import static com.intellij.psi.JavaTokenType.FINAL_KEYWORD;

public class JavaSimilarityFeaturesExtractor extends JavaRecursiveElementVisitor {
  private final @NotNull UsageSimilarityFeaturesRecorder myUsageSimilarityFeaturesRecorder;
  private final @NotNull PsiElement myContext;
  private final @NotNull HashSet<String> myVariableNames;
  private final @NotNull PsiElement myUsage;

  public JavaSimilarityFeaturesExtractor(@NotNull PsiElement usage, @NotNull PsiElement context) {
    myUsageSimilarityFeaturesRecorder = new UsageSimilarityFeaturesRecorder(context, usage);
    myContext = context;
    myVariableNames = collectVariableNames();
    myUsage = usage;
  }

  public @NotNull Bag getFeatures() {
    if (Registry.is("similarity.find.usages.java.clustering.enable")) {
      myContext.accept(this);
    }
    return myUsageSimilarityFeaturesRecorder.getFeatures();
  }

  @Override
  public void visitKeyword(@NotNull PsiKeyword keyword) {
    final IElementType type = keyword.getTokenType();
    if (!type.equals(FINAL_KEYWORD)) {
      myUsageSimilarityFeaturesRecorder.addFeature(type.toString());
    }
    super.visitKeyword(keyword);
  }

  @Override
  public void visitVariable(@NotNull PsiVariable variable) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(variable, "VAR: " + getTypeRepresentation(variable.getType()));
    super.visitVariable(variable);
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "NEW: " + getTypeRepresentation(expression));
    super.visitNewExpression(expression);
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(statement, "FOR");
    super.visitForStatement(statement);
  }

  @Override
  public void visitBlockStatement(@NotNull PsiBlockStatement statement) {
    if (Registry.is("similarity.analyze.only.simple.code.blocks")) {
      takeKeyFeaturesFromBlockStatement(statement);
    }
    else {
      super.visitBlockStatement(statement);
    }
  }

  private void takeKeyFeaturesFromBlockStatement(@NotNull PsiBlockStatement statement) {
    if (shouldCollectFeatures(statement)) {
      super.visitBlockStatement(statement);
    }
    else {
      myUsageSimilarityFeaturesRecorder.addAllFeatures(statement, "COMPLEX_BLOCK");
    }
  }

  private static boolean shouldCollectFeatures(@NotNull PsiBlockStatement statement) {
    PsiElement parentStatement = statement.getParent();
    return statement.getCodeBlock().getStatementCount() <= 1 ||
           !(parentStatement instanceof PsiLoopStatement || parentStatement instanceof PsiIfStatement);
  }

  @Override
  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(statement, "FOREACH");
    super.visitForeachStatement(statement);
  }

  @Override
  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(statement, "DO");
    super.visitDoWhileStatement(statement);
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(statement, "IF");
    super.visitIfStatement(statement);
  }

  @Override
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, expression.getOperationSign().getText());
    super.visitAssignmentExpression(expression);
  }

  @Override
  public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "instanceof");
    super.visitInstanceOfExpression(expression);
  }

  @Override
  public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, expression.getOperationTokenType().toString());
    super.visitPolyadicExpression(expression);
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(statement, "SWITCH");
    super.visitSwitchStatement(statement);
  }

  @Override
  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(statement, "WHILE");
    super.visitWhileStatement(statement);
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    if (Registry.is("similarity.find.usages.add.features.for.fields") && isField(expression)) {
      myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "FIELD: " + expression.getReferenceName());
    }
    if (!(expression instanceof PsiMethodReferenceExpression)) {
      if (!Registry.is("similarity.find.usages.fast.clustering")) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, getTypeRepresentation(expression));
      }
    }
    super.visitReferenceExpression(expression);
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    String tokenFeature;
    if (Registry.is("similarity.find.usages.fast.clustering")) {
      final PsiElement referenceNameElement = expression.getReferenceNameElement();
      tokenFeature = "{CALL: " + (referenceNameElement != null ? referenceNameElement.getText() : null) + "}";
    }
    else {
      tokenFeature = viaResolve(expression);
    }
    if (tokenFeature != null) {
      myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, tokenFeature);
    }
    super.visitMethodReferenceExpression(expression);
  }

  @Override
  public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
    PsiExpression arrayIndex = expression.getIndexExpression();
    String tokenFeature = "{ARRAY: data: " + expression.getType() + " index:" + getTypeRepresentation(arrayIndex) + "}";
    myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, tokenFeature);
    super.visitArrayAccessExpression(expression);
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    String tokenFeature;
    if (Registry.is("similarity.find.usages.fast.clustering")) {
      tokenFeature = "{CALL: " + expression.getMethodExpression().getReferenceName() + "}";
    }
    else {
      tokenFeature = getTokenFeatureViaResolve(expression);
    }
    if (tokenFeature != null) {
      myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, tokenFeature);
    }
    super.visitMethodCallExpression(expression);
  }

  @Override
  public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "literal:" + getTypeRepresentation(expression));
    super.visitLiteralExpression(expression);
  }

  @Override
  public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
    if(Registry.is("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class")){
      processComplexStructures(expression);
      return;
    }
    myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "lambda");
    super.visitLambdaExpression(expression);
  }

  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass clazz) {
    if(Registry.is("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class")){
      processComplexStructures(clazz);
      return;
    }
    myUsageSimilarityFeaturesRecorder.addAllFeatures(clazz, "anonymousClazz");
    super.visitAnonymousClass(clazz);
  }

  private void processComplexStructures(@NotNull PsiElement element){
    JavaSimilarityComplexExpressionFeaturesExtractor complexExpressionFeaturesExtractor =
      new JavaSimilarityComplexExpressionFeaturesExtractor(myUsage, element);

    complexExpressionFeaturesExtractor
      .getFeatures()
      .getBag()
      .forEach((String token, Integer count) -> IntStream.range(0, count).forEach(i -> myUsageSimilarityFeaturesRecorder.addFeature(token)));
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    if (method.equals(myContext)) {
      final MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
      myUsageSimilarityFeaturesRecorder.addFeature(method.isConstructor() ? "CONSTRUCTOR: " : methodSignature.getName());
      for (PsiType type : methodSignature.getParameterTypes()) {
        myUsageSimilarityFeaturesRecorder.addFeature("PARAMETER: " + type.getCanonicalText());
      }
      PsiType returnType = method.getReturnType();
      if (returnType != null) {
        myUsageSimilarityFeaturesRecorder.addFeature("RETURN_TYPE: " + returnType.getCanonicalText());
      }
    }
    else {
      super.visitMethod(method);
    }
  }

  private boolean isField(@NotNull PsiReferenceExpression referenceExpression) {
    return !isMethod(referenceExpression) &&
           (ContainerUtil.exists(referenceExpression.getChildren(), child -> child.getText().equals(".")) ||
            !myVariableNames.contains(referenceExpression.getReferenceName()));
  }

  private @NotNull HashSet<String> collectVariableNames() {
    PsiMethod containingMethod = getWrappingMethodForUsage(myContext);
    if (containingMethod == null) return new HashSet<>();
    PsiCodeBlock body = containingMethod.getBody();
    if (body == null) return new HashSet<>();
    HashSet<String> allVariables = new HashSet<>();
    allVariables.addAll(processFunctionParameters(containingMethod));
    allVariables.addAll(processFunctionStatements(body));
    return allVariables;
  }

  private static boolean isMethod(@NotNull PsiReferenceExpression expression) {
    return expression.getParent() instanceof PsiMethodCallExpression &&
           expression.getNextSibling() instanceof PsiExpressionList;
  }

  private static @Nullable PsiMethod getWrappingMethodForUsage(@NotNull PsiElement usage) {
    while (!(usage instanceof PsiMethod)) {
      usage = usage.getParent();
      if(usage instanceof PsiFile || usage == null) return null;
    }
    return (PsiMethod)usage;
  }

  private static @NotNull HashSet<String> processFunctionParameters(@NotNull PsiMethod containingMethod) {
    HashSet<String> variableNames = new HashSet<>();
    Arrays.stream(containingMethod.getParameterList().getParameters())
      .forEach(parameter -> variableNames.add(parameter.getName()));
    return variableNames;
  }

  private static @NotNull HashSet<String> processFunctionStatements(@NotNull PsiCodeBlock body) {
    HashSet<String> variableNames = new HashSet<>();
    for (PsiStatement statement : body.getStatements()) {
      if (statement instanceof PsiDeclarationStatement) {
        variableNames.addAll(processDeclarationStatement((PsiDeclarationStatement)statement));
        continue;
      }

      if (statement instanceof PsiLoopStatement) {
        variableNames.addAll(processLoopStatement(statement));
        continue;
      }

      if (statement instanceof PsiIfStatement) {
        Arrays.stream(statement.getChildren())
          .filter(child -> child instanceof PsiBlockStatement)
          .forEach(blockStatement -> variableNames
            .addAll(processFunctionStatements(((PsiBlockStatement)blockStatement).getCodeBlock())));
      }
    }
    return variableNames;
  }

  private static @NotNull HashSet<String> processLoopStatement(@NotNull PsiStatement statement) {
    HashSet<String> variableNames = new HashSet<>();
    if (statement instanceof PsiForeachStatement) {
      PsiParameter declaration = ((PsiForeachStatement)statement).getIterationParameter();
      variableNames.add((declaration).getName());
    }

    if (statement instanceof PsiForStatement) {
      PsiStatement initStatement = ((PsiForStatement)statement).getInitialization();
      if (initStatement instanceof PsiDeclarationStatement) {
        variableNames.addAll(processDeclarationStatement((PsiDeclarationStatement)initStatement));
      }
    }

    PsiStatement statementBody = ((PsiLoopStatement)statement).getBody();
    if (!(statementBody instanceof PsiBlockStatement)) return variableNames;
    variableNames.addAll(processFunctionStatements(((PsiBlockStatement)statementBody).getCodeBlock()));
    return variableNames;
  }

  private static @NotNull HashSet<String> processDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
    HashSet<String> variableNames = new HashSet<>();
    Arrays.stream(statement.getDeclaredElements()).forEach(element -> {
      if (element instanceof PsiLocalVariable) {
        variableNames.add(((PsiLocalVariable)element).getName());
      }
    });
    return variableNames;
  }

  private static @Nullable String viaResolve(@NotNull PsiMethodReferenceExpression expression) {
    final PsiElement resolve = expression.resolve();
    final PsiMethod method = ObjectUtils.tryCast(resolve, PsiMethod.class);
    if (method != null) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        return "{CALL: " +
               containingClass.getQualifiedName() +
               "." +
               method.getName() + "()" +
               " ret:" +
               getTypeRepresentation(expression) + "}";
      }
    }
    return null;
  }

  public static @NotNull String getTypeRepresentation(@Nullable PsiExpression expression) {
    if (expression == null) return "null";
    return getTypeRepresentation(expression.getType());
  }

  private static @NotNull String getTypeRepresentation(@Nullable PsiType type) {
    if (type != null) {
      return type.getCanonicalText();
    }
    else {
      return "null";
    }
  }

  private static @Nullable String getTokenFeatureViaResolve(@NotNull PsiMethodCallExpression expression) {
    String tokenFeature = null;
    final PsiReferenceExpression referenceExpression = expression.getMethodExpression();
    final PsiElement resolve = referenceExpression.resolve();
    final PsiMethod method = ObjectUtils.tryCast(resolve, PsiMethod.class);
    if (method != null) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        tokenFeature = "{CALL: " +
                       containingClass.getQualifiedName() +
                       "." +
                       method.getName() + "()" +
                       " ret:" +
                       getTypeRepresentation(referenceExpression) + " " +
                       getArgumentData(expression) + "}";
      }
    }
    return tokenFeature;
  }

  public static @NotNull String getArgumentData(@NotNull PsiMethodCallExpression methodCallExpression) {
    PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    StringBuilder arguments = new StringBuilder();
    for (int i = 0; i < expressions.length; i++) {
      arguments.append("arg").append(i).append(" type: ").append(getTypeRepresentation(expressions[i]));
    }
    return arguments.toString();
  }
}
