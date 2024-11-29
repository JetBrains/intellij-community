// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CastMethodArgumentFix extends MethodArgumentFix {
  private CastMethodArgumentFix(PsiExpressionList list, int i, PsiType toType, final ArgumentFixerActionFactory factory) {
    super(list, i, toType, factory);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpressionList list) {
    Presentation presentation = super.getPresentation(context, list);
    return presentation != null ? presentation.withPriority(PriorityAction.Priority.HIGH) : null;
  }

  @Override
  @NotNull String getText(@NotNull PsiExpressionList list) {
    String role = list.getExpressionCount() == 1
                  ? QuickFixBundle.message("fix.expression.role.argument")
                  : QuickFixBundle.message("fix.expression.role.nth.argument", myIndex + 1);
    boolean literal = AddTypeCastFix.createCastExpression(list.getExpressions()[myIndex], myToType) instanceof PsiLiteralExpression;
    return QuickFixBundle.message(literal ? "add.typecast.convert.text" : "add.typecast.cast.text", myToType.getPresentableText(), role);
  }

  private static class MyFixerActionFactory extends ArgumentFixerActionFactory {
    @Override
    public IntentionAction createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new CastMethodArgumentFix(list, i, toType, this).asIntention();
    }

    @Override
    protected PsiExpression getModifiedArgument(final PsiExpression expression, PsiType toType) throws IncorrectOperationException {
      final PsiType exprType = expression.getType();
      if (exprType instanceof PsiClassType && toType instanceof PsiPrimitiveType primitiveType) {
        PsiClassType boxed = primitiveType.getBoxedType(expression);
        assert boxed != null : toType + ":" + PsiUtil.getLanguageLevel(expression);
        toType = boxed;
      }
      return AddTypeCastFix.createCastExpression(expression, toType);
    }

    @Override
    public boolean areTypesConvertible(@NotNull PsiType exprType, @NotNull PsiType parameterType, @NotNull final PsiElement context) {
      if (exprType instanceof PsiClassType && parameterType instanceof PsiPrimitiveType primitiveType) {
        parameterType = primitiveType.getBoxedType(context); //unboxing from type of cast expression will take place at runtime
        if (parameterType == null) return false;
      }
      if (exprType instanceof PsiPrimitiveType && parameterType instanceof PsiClassType) {
        if (PsiTypes.nullType().equals(exprType)) {
          return true;
        }
        parameterType = PsiPrimitiveType.getUnboxedType(parameterType);
        if (parameterType == null) return false;
      }
      if (parameterType.isConvertibleFrom(exprType)) {
        return true;
      }

      return parameterType instanceof PsiEllipsisType ellipsisType &&
             areTypesConvertible(exprType, ellipsisType.getComponentType(), context);
    }
  }

  public static final ArgumentFixerActionFactory REGISTRAR = new MyFixerActionFactory();
}
