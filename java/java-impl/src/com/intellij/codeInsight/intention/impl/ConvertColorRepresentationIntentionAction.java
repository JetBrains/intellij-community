// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConvertColorRepresentationIntentionAction extends PsiUpdateModCommandAction<PsiNewExpression> {

  private static final String JAVA_AWT_COLOR = "java.awt.Color";
  private static final String COLOR_UI_RESOURCE = "javax.swing.plaf.ColorUIResource";

  public ConvertColorRepresentationIntentionAction() {
    super(PsiNewExpression.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiNewExpression expression) {
    if (!isJavaAwtColor(expression.getClassOrAnonymousClassReference()) || !isValueArguments(expression.getArgumentList())) return null;

    final PsiExpressionList arguments = expression.getArgumentList();
    if (arguments == null) return null;

    final PsiMethod constructor = expression.resolveConstructor();
    if (constructor == null) return null;

    final PsiExpressionList newArguments;
    try {
      newArguments = createNewArguments(JavaPsiFacade.getElementFactory(context.project()), 
                                        constructor.getParameterList().getParameters(), arguments.getExpressions());
    }
    catch (ConstantEvaluationOverflowException e) {
      return null;
    }

    if (newArguments == null) return null;

    return Presentation.of(JavaBundle.message("intention.convert.color.representation.text", newArguments.getText()))
      .withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiNewExpression expression, @NotNull ModPsiUpdater updater) {
    final PsiExpressionList arguments = expression.getArgumentList();
    if (arguments == null) return;

    final PsiMethod constructor = expression.resolveConstructor();
    if (constructor == null) return;

    final PsiExpressionList newArguments = createNewArguments(
      JavaPsiFacade.getElementFactory(context.project()),
      constructor.getParameterList().getParameters(),
      arguments.getExpressions()
    );

    if (newArguments == null) return;

    arguments.replace(newArguments);
  }

  @Nullable
  private static PsiExpressionList createNewArguments(@NotNull PsiElementFactory factory,
                                                      PsiParameter @NotNull [] parameters,
                                                      PsiExpression @NotNull [] arguments) {
    final String[] newValues = createArguments(parameters, arguments);
    if (newValues == null) return null;

    final PsiExpressionList result = ((PsiNewExpression)factory.createExpressionFromText("new Object()", parameters[0])).getArgumentList();
    if (result == null) return null;
    for (String value : newValues) {
      result.add(factory.createExpressionFromText(value, parameters[0]));
    }
    return result;
  }

  private static String @Nullable [] createArguments(PsiParameter @NotNull [] parameters,
                                                     PsiExpression @NotNull [] arguments) {
    if (parameters.length != arguments.length) return null;

    return switch (parameters.length) {
      case 1 -> createArguments(arguments[0]);
      case 2 -> createArguments(arguments[0], arguments[1]);
      case 3 -> createArguments(arguments[0], arguments[1], arguments[2]);
      case 4 -> createArguments(arguments[0], arguments[1], arguments[2], arguments[3]);
      default -> null;
    };
  }

  private static String @Nullable [] createArguments(@NotNull PsiExpression rgbExpression) {
    return createArguments(rgbExpression, false);
  }

  private static String @Nullable [] createArguments(@NotNull PsiExpression rgbExpression,
                                                     @NotNull PsiExpression hasAlphaExpression) {
    final Boolean hasAlpha = computeBoolean(hasAlphaExpression);
    if (hasAlpha == null) {
      return null;
    }
    return createArguments(rgbExpression, hasAlpha);
  }

  private static String @Nullable [] createArguments(@NotNull PsiExpression rExpression,
                                                     @NotNull PsiExpression gExpression,
                                                     @NotNull PsiExpression bExpression) {
    final Integer value = createInt(computeInteger(rExpression), computeInteger(gExpression), computeInteger(bExpression));
    return value != null ? new String[]{"0x" + Integer.toHexString(value)} : null;
  }

  private static String @Nullable [] createArguments(@NotNull PsiExpression rExpression,
                                                     @NotNull PsiExpression gExpression,
                                                     @NotNull PsiExpression bExpression,
                                                     @NotNull PsiExpression aExpression) {
    final Integer value = createInt(computeInteger(aExpression), computeInteger(rExpression), computeInteger(gExpression), computeInteger(bExpression));
    if (value == null) return null;

    return new String[]{
      "0x" + Integer.toHexString(value),
      "true",
    };
  }

  private static String @Nullable [] createArguments(@NotNull PsiExpression rgbExpression,
                                                     boolean hasAlpha) {
    final Integer argb = computeInteger(rgbExpression);
    if (argb == null) return null;

    final String[] result;
    if (hasAlpha) {
      result = new String[4]; // (r, g, b, a)
      result[3] = String.valueOf(argb >> (3 * Byte.SIZE) & 0xFF);
    }
    else {
      result = new String[3]; // (r, g, b)
    }
    for (int i = 0; i < 3; i++) {
      result[2 - i] = String.valueOf(argb >> (i * Byte.SIZE) & 0xFF);
    }
    return result;
  }

  @Nullable
  private static Integer createInt(Integer... ints) {
    int result = 0;
    for (Integer i : ints) {
      if (i == null) return null;
      result = result << Byte.SIZE | (i & 0xFF);
    }
    return result;
  }

  @Nullable
  private static Integer computeInteger(@NotNull PsiExpression expr) {
    final Object result = compute(expr);
    return result instanceof Integer ? (Integer)result : null;
  }

  @Nullable
  private static Boolean computeBoolean(@NotNull PsiExpression expr) {
    final Object result = compute(expr);
    return result instanceof Boolean ? (Boolean)result : null;
  }

  @Nullable
  private static Object compute(@NotNull PsiExpression expr) {
    return JavaConstantExpressionEvaluator.computeConstantExpression(expr, true);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.convert.color.representation.family");
  }

  private static boolean isJavaAwtColor(@Nullable PsiJavaCodeReferenceElement ref) {
    final String fqn = getFqn(ref);
    return JAVA_AWT_COLOR.equals(fqn) || COLOR_UI_RESOURCE.equals(fqn);
  }

  @Nullable
  private static String getFqn(@Nullable PsiJavaCodeReferenceElement ref) {
    if (ref != null) {
      final PsiReference reference = ref.getReference();
      if (reference != null && reference.resolve() instanceof PsiClass cls) {
        return cls.getQualifiedName();
      }
    }
    return null;
  }

  private static boolean isValueArguments(@Nullable PsiExpressionList arguments) {
    if (arguments == null) return false;

    for (PsiExpression argument : arguments.getExpressions()) {
      if (argument instanceof PsiReferenceExpression) return false;
    }

    return true;
  }
}
