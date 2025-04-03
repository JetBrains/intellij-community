// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaFutureKeywordUseFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    PsiTypeElement typeElement = ObjectUtils.tryCast(ref.getParent(), PsiTypeElement.class);
    if (typeElement == null || typeElement.getFirstChild() != typeElement.getLastChild()) return;
    PsiElement parent = typeElement.getParent();
    if (JavaKeywords.VAR.equals(ref.getReferenceName())) {
      registerSetVariableTypeFix(parent, registrar);
      registerLambdaParametersFix(parent, registrar);
      registerVarLanguageLevelFix(ref, parent, registrar);
    }
    if (JavaKeywords.RECORD.equals(ref.getReferenceName())) {
      registerRecordLanguageLevelFix(ref, parent, registrar);
    }
  }

  private static void registerRecordLanguageLevelFix(@NotNull PsiJavaCodeReferenceElement ref,
                                                     PsiElement parent,
                                                     @NotNull QuickFixActionRegistrar registrar) {
    if ((parent instanceof PsiMethod || parent instanceof PsiField) && parent.getParent() instanceof PsiClass) {
      // record R() {} is parsed as method if records aren't supported
      // record R incomplete declaration is also possible
      registerIncreaseLevelFixes(ref, JavaFeature.RECORDS, registrar);
    }
    if (parent instanceof PsiLocalVariable && parent.getParent() instanceof PsiDeclarationStatement
        && ((PsiDeclarationStatement)parent.getParent()).getDeclaredElements().length == 1) {
      // record R() declaration inside method
      registerIncreaseLevelFixes(ref, JavaFeature.RECORDS, registrar);
    }
  }

  private static void registerIncreaseLevelFixes(@NotNull PsiJavaCodeReferenceElement ref,
                                                 @NotNull JavaFeature feature,
                                                 @NotNull QuickFixActionRegistrar registrar) {
    for (CommonIntentionAction fix : HighlightFixUtil.getIncreaseLanguageLevelFixes(ref, feature)) {
      registrar.register(fix.asIntention());
    }
  }

  private static void registerVarLanguageLevelFix(@NotNull PsiJavaCodeReferenceElement ref,
                                                  PsiElement parent,
                                                  @NotNull QuickFixActionRegistrar registrar) {
    JavaFeature feature;
    if (parent instanceof PsiParameter && ((PsiParameter)parent).getDeclarationScope() instanceof PsiLambdaExpression) {
      feature = JavaFeature.VAR_LAMBDA_PARAMETER;
    }
    else {
      feature = JavaFeature.LVTI;
    }
    registerIncreaseLevelFixes(ref, feature, registrar);
  }

  @Override
  public @NotNull Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }

  private static void registerLambdaParametersFix(PsiElement parent, @NotNull QuickFixActionRegistrar registrar) {
    PsiVariable variable = ObjectUtils.tryCast(parent, PsiVariable.class);
    if (variable == null) return;
    PsiParameterList parameterList = ObjectUtils.tryCast(variable.getParent(), PsiParameterList.class);
    if (parameterList == null) return;
    PsiLambdaExpression lambdaExpression = ObjectUtils.tryCast(parameterList.getParent(), PsiLambdaExpression.class);
    if (lambdaExpression == null) return;
    registrar.register(PriorityIntentionActionWrapper.highPriority(
      QuickFixFactory.getInstance().createRemoveRedundantLambdaParameterTypesFix(lambdaExpression, JavaBundle.message(
        "remove.var.keyword.text"))));
  }

  private static void registerSetVariableTypeFix(PsiElement parent, @NotNull QuickFixActionRegistrar registrar) {
    PsiVariable variable = ObjectUtils.tryCast(parent, PsiVariable.class);
    if (variable == null) return;
    PsiType type = inferType(variable);
    if (type == null) return;
    registrar.register(PriorityIntentionActionWrapper.highPriority(QuickFixFactory.getInstance().createSetVariableTypeFix(variable, type)));
  }

  private static @Nullable PsiType inferType(@NotNull PsiVariable variable) {
    if (variable instanceof PsiParameter && variable.getParent() instanceof PsiForeachStatement foreach) {
      PsiExpression iteratedValue = foreach.getIteratedValue();
      return iteratedValue != null ? JavaGenericsUtil.getCollectionItemType(iteratedValue) : null;
    }
    else if (variable instanceof PsiLocalVariable) {
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null) return null;
      PsiType type = initializer.getType();
      return PsiTypesUtil.isDenotableType(type, variable) && !PsiTypes.voidType().equals(type) ? type : null;
    }
    return null;
  }
}
