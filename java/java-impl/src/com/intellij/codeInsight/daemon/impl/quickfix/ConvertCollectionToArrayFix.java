// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertCollectionToArrayFix extends PsiUpdateModCommandAction<PsiExpression> {
  private final @NotNull SmartPsiElementPointer<@NotNull PsiExpression> myCollectionPointer;
  @NonNls private final String myNewArrayText;

  public ConvertCollectionToArrayFix(@NotNull PsiExpression collectionExpression,
                                     @NotNull PsiExpression expressionToReplace,
                                     @NotNull PsiArrayType arrayType) {
    this(collectionExpression, expressionToReplace,
         TypeUtils.isJavaLangObject(arrayType.getComponentType()) ? "" : "new " + getArrayTypeText(arrayType.getComponentType()));
  }

  private ConvertCollectionToArrayFix(@NotNull PsiExpression collectionExpression,
                                      @NotNull PsiExpression expressionToReplace,
                                      @NotNull String newArrayText) {
    super(expressionToReplace);
    myCollectionPointer = SmartPointerManager.createPointer(collectionExpression);
    myNewArrayText = newArrayText;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("collection.to.array.family.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression expressionToReplace) {
    if (myCollectionPointer.getElement() == null) return null;
    return Presentation.of(QuickFixBundle.message("collection.to.array.text", myNewArrayText));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression expressionToReplace, @NotNull ModPsiUpdater updater) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    PsiExpression collectionExpression = myCollectionPointer.getElement();
    if (collectionExpression == null) return;
    String replacement = ParenthesesUtils.getText(collectionExpression, ParenthesesUtils.POSTFIX_PRECEDENCE) +
                         ".toArray(" + myNewArrayText + ")";
    expressionToReplace.replace(factory.createExpressionFromText(replacement, collectionExpression));
  }

  @NotNull
  private static String getArrayTypeText(PsiType componentType) {
    if (componentType instanceof PsiArrayType arrayType) {
      return getArrayTypeText(arrayType.getComponentType()) + "[]";
    }
    if (componentType instanceof PsiClassType classType) {
      return classType.rawType().getCanonicalText() + "[0]";
    }
    return componentType.getCanonicalText() + "[0]";
  }
}
