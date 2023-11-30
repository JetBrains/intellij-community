// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AddNewArrayExpressionFix extends PsiUpdateModCommandAction<PsiArrayInitializerExpression> {
  private final PsiType myType;

  public AddNewArrayExpressionFix(@NotNull PsiArrayInitializerExpression initializer) {
    super(initializer);
    myType = getType(initializer);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiArrayInitializerExpression element) {
    return myType == null ? null :
           Presentation.of(QuickFixBundle.message("add.new.array.text", myType.getPresentableText())).withFixAllOption(this);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.new.array.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiArrayInitializerExpression initializer, @NotNull ModPsiUpdater updater) {
    if (myType == null) return;
    doFix(myType, initializer);
  }

  public static void doFix(@NotNull PsiArrayInitializerExpression initializer) {
    PsiType type = getType(initializer);
    if (type == null) return;
    doFix(type, initializer);
  }
  
  private static void doFix(@NotNull PsiType type, @NotNull PsiArrayInitializerExpression initializer) {
    Project project = initializer.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    @NonNls String text = "new " + type.getCanonicalText() + "[]{}";
    PsiNewExpression newExpr = (PsiNewExpression) factory.createExpressionFromText(text, null);
    Objects.requireNonNull(newExpr.getArrayInitializer()).replace(initializer);
    newExpr = (PsiNewExpression) CodeStyleManager.getInstance(project).reformat(newExpr);
    initializer.replace(newExpr);
  }

  private static PsiType getType(@NotNull PsiArrayInitializerExpression initializer) {
    final PsiExpression[] initializers = initializer.getInitializers();
    final PsiElement parent = initializer.getParent();
    if (!(parent instanceof PsiAssignmentExpression assignmentExpression)) {
      if (initializers.length == 0) return null;
      return validateType(initializers[0].getType(), parent);
    }
    final PsiType type = assignmentExpression.getType();
    if (type instanceof PsiArrayType arrayType) {
      return validateType(arrayType.getComponentType(), parent);
    }
    if (initializers.length == 0) return null;
    return validateType(initializers[0].getType(), parent);
  }

  private static PsiType validateType(PsiType type, @NotNull PsiElement context) {
    if (PsiTypes.nullType().equals(type)) return null;
    return LambdaUtil.notInferredType(type) || !PsiTypesUtil.isDenotableType(type, context) ? 
           null : TypeConversionUtil.erasure(type);
  }
}
