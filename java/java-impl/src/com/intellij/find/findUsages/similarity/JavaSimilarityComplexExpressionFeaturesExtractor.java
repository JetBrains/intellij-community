// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesRecorder;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.JavaTokenType.FINAL_KEYWORD;

public class JavaSimilarityComplexExpressionFeaturesExtractor extends JavaRecursiveElementVisitor {
  private static final int MAX_SECONDARY_FEATURES = 20;
  private static final int MAX_PRIMARY_FEATURES = 50;
  private final @NotNull UsageSimilarityFeaturesRecorder myUsageSimilarityFeaturesRecorder;
  private final @NotNull PsiElement myContext;

  public JavaSimilarityComplexExpressionFeaturesExtractor(@NotNull PsiElement usage, @NotNull PsiElement context) {
    myUsageSimilarityFeaturesRecorder = new UsageSimilarityFeaturesRecorder(context, usage);
    myContext = context;
  }

  public @NotNull Bag getFeatures() {
    myContext.accept(this);
    return myUsageSimilarityFeaturesRecorder.getFeatures();
  }

  private void addPrimaryFeatures(@NotNull PsiElement element, @NotNull String token) {
    if (myUsageSimilarityFeaturesRecorder.getFeatures().getCardinality() < MAX_SECONDARY_FEATURES) {
      myUsageSimilarityFeaturesRecorder.addAllFeatures(element, token);
      return;
    }
    if (myUsageSimilarityFeaturesRecorder.getFeatures().getCardinality() < MAX_PRIMARY_FEATURES) {
      myUsageSimilarityFeaturesRecorder.addFeature(token);
    }
  }

  private void addSecondaryFeatures(@NotNull String token) {
    if (myUsageSimilarityFeaturesRecorder.getFeatures().getCardinality() < MAX_SECONDARY_FEATURES) {
      myUsageSimilarityFeaturesRecorder.addFeature(token);
    }
  }

  @Override
  public void visitKeyword(@NotNull PsiKeyword keyword) {
    final IElementType type = keyword.getTokenType();
    if (!type.equals(FINAL_KEYWORD)) {
      myUsageSimilarityFeaturesRecorder.addFeature(type.toString());
    }
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    final PsiElement referenceNameElement = expression.getReferenceNameElement();
    addPrimaryFeatures(expression, "{CALL: " + (referenceNameElement != null ? referenceNameElement.getText() : null) + "}");
    super.visitMethodReferenceExpression(expression);
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    addPrimaryFeatures(expression, "{CALL: " + expression.getMethodExpression().getReferenceName() + "}");
    super.visitMethodCallExpression(expression);
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    final MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    myUsageSimilarityFeaturesRecorder.addFeature(method.isConstructor() ? "CONSTRUCTOR: " : methodSignature.getName());
    for (PsiType type : methodSignature.getParameterTypes()) {
      myUsageSimilarityFeaturesRecorder.addFeature("PARAMETER: " + type.getCanonicalText());
    }
    PsiType returnType = method.getReturnType();
    if (returnType != null) {
      myUsageSimilarityFeaturesRecorder.addFeature("RETURN_TYPE: " + returnType.getCanonicalText());
    }
    if (!method.equals(myContext)) {
      super.visitMethod(method);
    }
  }

  @Override
  public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
    addSecondaryFeatures("lambda");
    super.visitLambdaExpression(expression);
  }

  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass clazz) {
    addSecondaryFeatures("anonymousClazz");
    super.visitAnonymousClass(clazz);
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    addSecondaryFeatures("FOR");
    super.visitForStatement(statement);
  }

  @Override
  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    addSecondaryFeatures("FOREACH");
    super.visitForeachStatement(statement);
  }

  @Override
  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    addSecondaryFeatures("DO");
    super.visitDoWhileStatement(statement);
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    addSecondaryFeatures("IF");
    super.visitIfStatement(statement);
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    addSecondaryFeatures("SWITCH");
    super.visitSwitchStatement(statement);
  }

  @Override
  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    addSecondaryFeatures("WHILE");
    super.visitWhileStatement(statement);
  }
}
