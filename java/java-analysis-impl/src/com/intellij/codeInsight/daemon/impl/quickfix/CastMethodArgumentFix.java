// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CastMethodArgumentFix extends MethodArgumentFix implements HighPriorityAction {
  private final @IntentionName String myText;

  private CastMethodArgumentFix(PsiExpressionList list, int i, PsiType toType, final ArgumentFixerActionFactory factory) {
    super(list, i, toType, factory);
    myText = list.getExpressionCount() == 1
             ? QuickFixBundle.message("cast.single.parameter.text", myToType.getPresentableText())
             : QuickFixBundle.message("cast.parameter.text", myIndex + 1, myToType.getPresentableText());
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  private static class MyFixerActionFactory extends ArgumentFixerActionFactory {
    @Override
    public CastMethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new CastMethodArgumentFix(list, i, toType, this);
    }

    @Override
    protected PsiExpression getModifiedArgument(final PsiExpression expression, PsiType toType) throws IncorrectOperationException {
      final PsiType exprType = expression.getType();
      if (exprType instanceof PsiClassType && toType instanceof PsiPrimitiveType) {
        toType = ((PsiPrimitiveType)toType).getBoxedType(expression);
        assert toType != null;
      }
      return AddTypeCastFix.createCastExpression(expression, expression.getProject(), toType);
    }

    @Override
    public boolean areTypesConvertible(@NotNull PsiType exprType, @NotNull PsiType parameterType, @NotNull final PsiElement context) {
      if (exprType instanceof PsiClassType && parameterType instanceof PsiPrimitiveType) {
        parameterType = ((PsiPrimitiveType)parameterType).getBoxedType(context); //unboxing from type of cast expression will take place at runtime
        if (parameterType == null) return false;
      }
      if (exprType instanceof PsiPrimitiveType && parameterType instanceof PsiClassType) {
        if (PsiType.NULL.equals(exprType)) {
          return true;
        }
        parameterType = PsiPrimitiveType.getUnboxedType(parameterType);
        if (parameterType == null) return false;
      }
      if (parameterType.isConvertibleFrom(exprType)) {
        return true;
      }

      return parameterType instanceof PsiEllipsisType &&
             areTypesConvertible(exprType, ((PsiEllipsisType)parameterType).getComponentType(), context);
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiExpressionList list = myArgList.getElement();
    if (list == null) return null;
    return new CastMethodArgumentFix(PsiTreeUtil.findSameElementInCopy(list, target), myIndex, myToType,
                                     myArgumentFixerActionFactory);
  }

  public static final ArgumentFixerActionFactory REGISTRAR = new MyFixerActionFactory();
}
