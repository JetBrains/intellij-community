// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class WrapObjectWithOptionalOfNullableFix extends MethodArgumentFix {
  public static final ArgumentFixerActionFactory REGISTAR = new MyFixerActionFactory();

  protected WrapObjectWithOptionalOfNullableFix(final @NotNull PsiExpressionList list,
                                                final int i,
                                                final @NotNull PsiType toType,
                                                final @NotNull ArgumentFixerActionFactory fixerActionFactory) {
    super(list, i, toType, fixerActionFactory);
  }

  @Override
  public @NotNull String getText(@NotNull PsiExpressionList list) {
    if (list.getExpressionCount() == 1) {
      return QuickFixBundle.message("wrap.with.optional.single.parameter.text");
    }
    else {
      return QuickFixBundle.message("wrap.with.optional.parameter.text", myIndex + 1);
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpressionList list) {
    if (!PsiUtil.isAvailable(JavaFeature.STREAM_OPTIONAL, context.file())) return null;
    Presentation presentation = super.getPresentation(context, list);
    return presentation == null ? null : presentation.withPriority(PriorityAction.Priority.HIGH);
  }

  public static IntentionAction createFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    return new MyFix(expression, type).asIntention();
  }

  private static class MyFix extends PsiUpdateModCommandAction<PsiExpression> {
    private final PsiType myType;

    protected MyFix(@NotNull PsiExpression element, @Nullable PsiType type) {
      super(element);
      myType = type;
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return QuickFixBundle.message("wrap.with.optional.single.parameter.text");
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression expression, @NotNull ModPsiUpdater updater) {
      expression.replace(getModifiedExpression(expression));
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression expression) {
      if (!(PsiUtil.isAvailable(JavaFeature.STREAM_OPTIONAL, expression) && areConvertible(expression.getType(), myType))) return null;
      return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.HIGH);
    }
  }

  public static class MyFixerActionFactory extends ArgumentFixerActionFactory {

    @Override
    protected @Nullable PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
      return getModifiedExpression(expression);
    }

    @Override
    public boolean areTypesConvertible(final @NotNull PsiType exprType, final @NotNull PsiType parameterType, final @NotNull PsiElement context) {
      return parameterType.isConvertibleFrom(exprType) || areConvertible(exprType, parameterType);
    }

    @Override
    public IntentionAction createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new WrapObjectWithOptionalOfNullableFix(list, i, toType, this).asIntention();
    }
  }

  private static boolean areConvertible(@Nullable PsiType exprType, @Nullable PsiType parameterType) {
    if (exprType == null ||
        !exprType.isValid() ||
        !(parameterType instanceof PsiClassType) ||
        !parameterType.isValid()) {
      return false;
    }
    final PsiClassType.ClassResolveResult resolve = ((PsiClassType)parameterType).resolveGenerics();
    final PsiClass resolvedClass = resolve.getElement();
    if (resolvedClass == null || !CommonClassNames.JAVA_UTIL_OPTIONAL.equals(resolvedClass.getQualifiedName())) return false;

    final Collection<PsiType> values = resolve.getSubstitutor().getSubstitutionMap().values();
    if (values.isEmpty()) return true;
    if (values.size() > 1) return false;
    final PsiType optionalTypeParameter = ContainerUtil.getFirstItem(values);
    if (optionalTypeParameter == null) return false;
    return TypeConversionUtil.isAssignable(optionalTypeParameter, exprType);
  }

  private static @NotNull PsiExpression getModifiedExpression(PsiExpression expression) {
    final Project project = expression.getProject();
    final Nullability nullability = NullabilityUtil.getExpressionNullability(expression, true);
    String methodName = nullability == Nullability.NOT_NULL ? "of" : "ofNullable";
    final String newExpressionText = CommonClassNames.JAVA_UTIL_OPTIONAL + "." + methodName + "(" + expression.getText() + ")";
    return JavaPsiFacade.getElementFactory(project).createExpressionFromText(newExpressionText, expression);
  }
}
