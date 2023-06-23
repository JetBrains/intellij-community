// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplacePrimitiveWithBoxedTypeAction extends PsiUpdateModCommandAction<PsiTypeElement> {
  private final String myPrimitiveName;
  private final String myBoxedTypeName;
  private static final Logger LOG = Logger.getInstance(ReplacePrimitiveWithBoxedTypeAction.class);

  public ReplacePrimitiveWithBoxedTypeAction(@NotNull PsiTypeElement element, @NotNull String typeName, @NotNull String boxedTypeName) {
    super(element);
    myPrimitiveName = typeName;
    myBoxedTypeName = boxedTypeName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("convert.primitive.to.boxed.type");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (type instanceof PsiWildcardType wildcardType) {
      type = wildcardType.getBound();
    }
    if (!(type instanceof PsiPrimitiveType primitiveType) || primitiveType.getBoxedType(typeElement) == null) return null;
    return Presentation.of(CommonQuickFixBundle.message("fix.replace.x.with.y", myPrimitiveName, myBoxedTypeName));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiTypeElement typeElement, @NotNull ModPsiUpdater updater) {
    final PsiType type = typeElement.getType();
    PsiType boxedType;
    if (type instanceof PsiPrimitiveType primitiveType) {
      boxedType = primitiveType.getBoxedType(typeElement);
    } else {
      LOG.assertTrue(type instanceof PsiWildcardType);
      final PsiWildcardType wildcardType = (PsiWildcardType)type;
      final PsiClassType boxedBound = ((PsiPrimitiveType)wildcardType.getBound()).getBoxedType(typeElement);
      LOG.assertTrue(boxedBound != null);
      boxedType = wildcardType.isExtends() ? PsiWildcardType.createExtends(typeElement.getManager(), boxedBound) 
                                           : PsiWildcardType.createSuper(typeElement.getManager(), boxedBound);
    }
    LOG.assertTrue(boxedType != null);
    typeElement.replace(JavaPsiFacade.getElementFactory(context.project()).createTypeElement(boxedType));
  }
}
