// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesRecorder;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.JavaTokenType.FINAL_KEYWORD;

public class JavaSimilarityFeaturesExtractor extends JavaRecursiveElementVisitor {
  private final @NotNull UsageSimilarityFeaturesRecorder myUsageSimilarityFeaturesRecorder;
  private final @NotNull PsiElement myContext;

  public JavaSimilarityFeaturesExtractor(@NotNull PsiElement usage, @NotNull PsiElement context) {
    myUsageSimilarityFeaturesRecorder = new UsageSimilarityFeaturesRecorder(context, usage);
    myContext = context;
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
    String tokenFeature = null;
    if (!(expression instanceof PsiMethodReferenceExpression)) {
      tokenFeature = null;
      if (!Registry.is("similarity.find.usages.fast.clustering")) {
        tokenFeature += getTypeRepresentation(expression);
      }
    }

    if (tokenFeature != null) {
      myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, tokenFeature);
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
    myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "lambda");
    super.visitLambdaExpression(expression);
  }

  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass clazz) {
    myUsageSimilarityFeaturesRecorder.addAllFeatures(clazz, "anonymousClazz");
    super.visitAnonymousClass(clazz);
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
