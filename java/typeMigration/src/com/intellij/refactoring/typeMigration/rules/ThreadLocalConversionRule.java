// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableAccessFromInnerClassFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ThreadLocalConversionRule extends TypeConversionRule {
  private static final Logger LOG = Logger.getInstance(ThreadLocalConversionRule.class);

  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from,
                                                     PsiType to,
                                                     PsiMember member,
                                                     PsiExpression context,
                                                     TypeMigrationLabeler labeler) {
    if (to instanceof PsiClassType toClassType && isThreadLocalTypeMigration(from, toClassType, context)) {
      return findDirectConversion(context, toClassType, from, labeler);
    }
    return null;
  }

  private static boolean isThreadLocalTypeMigration(PsiType from, PsiClassType to, PsiExpression context) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
    final PsiClass threadLocalClass = resolveResult.getElement();

    if (threadLocalClass != null) {
      final String typeQualifiedName = threadLocalClass.getQualifiedName();
      if (!Comparing.strEqual(typeQualifiedName, ThreadLocal.class.getName())) {
        return false;
      }
      final PsiTypeParameter[] typeParameters = threadLocalClass.getTypeParameters();
      if (typeParameters.length != 1) return !PsiUtil.isLanguageLevel5OrHigher(context);
      final PsiType toTypeParameterValue = resolveResult.getSubstitutor().substitute(typeParameters[0]);
      if (toTypeParameterValue != null) {
        if (from instanceof PsiPrimitiveType) {
          final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(toTypeParameterValue);
          if (unboxedInitialType != null) {
            return TypeConversionUtil.areTypesConvertible(from, unboxedInitialType);
          }
        }
        else {
          return TypeConversionUtil.isAssignable(toTypeParameterValue, from);
        }
      }
      return !PsiUtil.isLanguageLevel5OrHigher(context);
    }
    return false;
  }

  @Nullable
  private static TypeConversionDescriptor findDirectConversion(PsiElement context,
                                                               PsiClassType to,
                                                               PsiType from,
                                                               TypeMigrationLabeler labeler) {
    final PsiClass toTypeClass = to.resolve();
    LOG.assertTrue(toTypeClass != null);

    final PsiElement parent = context.getParent();
    if (parent.equals(labeler.getCurrentRoot().getElement()) && ((PsiVariable)parent).getInitializer() == context) {

      return wrapWithNewExpression(from, to, (PsiExpression)context);
    }
    if (context instanceof PsiArrayAccessExpression) {
      return new TypeConversionDescriptor("$qualifier$[$val$]", "$qualifier$.get()[$val$]");
    }
    if (parent instanceof PsiAssignmentExpression) {
      final IElementType operationSign = ((PsiAssignmentExpression)parent).getOperationTokenType();
      if (operationSign == JavaTokenType.EQ) {
        final boolean rightInfected = ((PsiAssignmentExpression)parent).getLExpression() == context;
        final String replacement = rightInfected
                                   ? "$qualifier$ = $val$.get()"
                                   : "$qualifier$.set(" + coerceType("$val$", from, to, context) + ")";
        return new TypeConversionDescriptor("$qualifier$ = $val$", replacement, (PsiAssignmentExpression)parent);
      }
    }
    if (context instanceof PsiReferenceExpression) {
      final PsiExpression qualifierExpression = ((PsiReferenceExpression)context).getQualifierExpression();
      final PsiExpression expression = context.getParent() instanceof PsiMethodCallExpression && qualifierExpression != null
                                       ? qualifierExpression
                                       : (PsiExpression)context;
      return new TypeConversionDescriptor("$qualifier$", toPrimitive("$qualifier$.get()", from, context), expression);
    }
    if (context instanceof PsiPrefixExpression prefixExpression &&
        prefixExpression.getOperationSign().getTokenType() == JavaTokenType.EXCL) {
      return new TypeConversionDescriptor("!$qualifier$", "!$qualifier$.get()");
    }
    if (parent instanceof PsiExpressionStatement) {
      if (context instanceof PsiPostfixExpression postfixExpression) {
        final String sign = postfixExpression.getOperationSign().getText();

        return new TypeConversionDescriptor("$qualifier$" + sign,
                                            "$qualifier$.set(" +
                                            getBoxedWrapper(from,
                                                            to,
                                                            toPrimitive("$qualifier$.get()", from, context) + " " + sign.charAt(0) + " 1",
                                                            labeler,
                                                            context,
                                                            postfixExpression.getOperand().getText() + sign.charAt(0) + " 1") +
                                            ")");
      }
      else if (context instanceof PsiPrefixExpression prefixExpression) {
        final String sign = prefixExpression.getOperationSign().getText();
        final PsiExpression operand = prefixExpression.getOperand();
        return new TypeConversionDescriptor(sign + "$qualifier$",
                                            "$qualifier$.set(" +
                                            getBoxedWrapper(from,
                                                            to,
                                                            toPrimitive("$qualifier$.get()", from, context) + " " + sign.charAt(0) + " 1",
                                                            labeler,
                                                            context,
                                                            operand != null ? operand.getText() + sign.charAt(0) + " 1" : null) +
                                            ")");
      }
      else if (context instanceof PsiAssignmentExpression assignmentExpression) {
        final PsiJavaToken signToken = assignmentExpression.getOperationSign();
        final IElementType operationSign = signToken.getTokenType();
        final String sign = signToken.getText();
        final PsiExpression lExpression = assignmentExpression.getLExpression();
        if (operationSign == JavaTokenType.EQ) {
          if (lExpression instanceof PsiReferenceExpression) {
            final PsiElement element = ((PsiReferenceExpression)lExpression).resolve();
            if (element instanceof PsiVariable && ((PsiVariable)element).hasModifierProperty(PsiModifier.FINAL)) {
              return wrapWithNewExpression(from, to, ((PsiAssignmentExpression)context).getRExpression());
            }
          }
          return new TypeConversionDescriptor("$qualifier$ = $val$",
                                              "$qualifier$.set(" + coerceType("$val$", from, to, context) + ")");
        }
        else {
          final PsiExpression rExpression = assignmentExpression.getRExpression();
          String boxedWrapper = getBoxedWrapper(from, to, toPrimitive("$qualifier$.get()", from, context) + " " + sign.charAt(0) + " $val$",
                                                labeler, context,
                                                rExpression != null
                                                ? lExpression.getText() + sign.charAt(0) + rExpression.getText()
                                                : null);
          return new TypeConversionDescriptor("$qualifier$" + sign + "$val$", "$qualifier$.set(%s)".formatted(boxedWrapper));
        }
      }
    }
    return null;
  }

  private static TypeConversionDescriptor wrapWithNewExpression(PsiType from, PsiClassType to, PsiExpression initializer) {
    List<PsiVariable> toMakeFinal = TypeConversionRuleUtil.getVariablesToMakeFinal(initializer);
    if (toMakeFinal == null) return null;
    return new WrappingWithInnerClassOrLambdaDescriptor("$qualifier$",
                                                        createThreadLocalInitializerReplacement(from, to, initializer),
                                                        initializer,
                                                        toMakeFinal);
  }

  private static @NonNls String createThreadLocalInitializerReplacement(PsiType from, PsiClassType to, PsiElement context) {
    if (PsiUtil.isLanguageLevel8OrHigher(context)) {
      return "java.lang.ThreadLocal.withInitial(() -> " + coerceType("$qualifier$", from, to, context) + ")";
    }
    final StringBuilder result = new StringBuilder("new ");
    result.append(to.getCanonicalText()).append("() {\n");
    if (PsiUtil.isLanguageLevel5OrHigher(context)) {
      result.append("  @").append(CommonClassNames.JAVA_LANG_OVERRIDE).append("\n");
    }
    result.append("  protected ")
      .append(PsiUtil.isLanguageLevel5OrHigher(context) ? to.getParameters()[0].getCanonicalText() : CommonClassNames.JAVA_LANG_OBJECT)
      .append(" initialValue() {\n")
      .append("    return ")
      .append(coerceType("$qualifier$", from, to, context)).append(";\n")
      .append("  }\n")
      .append("}");
    return result.toString();
  }

  private static @NonNls String toPrimitive(@NonNls String replaceByArg, PsiType from, PsiElement context) {
    if (PsiUtil.isLanguageLevel5OrHigher(context)) {
      return replaceByArg;
    }
    return from instanceof PsiPrimitiveType
           ? "((" + ((PsiPrimitiveType)from).getBoxedTypeName() + ")" + replaceByArg + ")." + from.getCanonicalText() + "Value()"
           : "((" + from.getCanonicalText() + ")" + replaceByArg + ")";
  }

  private static @NonNls String coerceType(@NonNls String replaceByArg, PsiType from, PsiClassType to, PsiElement context) {
    if (PsiUtil.isLanguageLevel5OrHigher(context)) {
      if (from instanceof PsiPrimitiveType) {
        final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(to.getParameters()[0]);
        if (unboxed != null && !from.equals(unboxed)) {
          if (PsiTypes.longType().equals(unboxed)) {
            String result = longLiteralText(context, replaceByArg);
            if (result != null) return result;
          }
          return context instanceof PsiExpression expression &&
                 PsiPrecedenceUtil.getPrecedence(expression) > PsiPrecedenceUtil.TYPE_CAST_PRECEDENCE
                 ? "(" + unboxed.getCanonicalText() + ")(" + replaceByArg + ")"
                 : "(" + unboxed.getCanonicalText() + ")" + replaceByArg;
        }
      }
      return replaceByArg;
    }
    return from instanceof PsiPrimitiveType
           ? "new " + ((PsiPrimitiveType)from).getBoxedTypeName() + "(" + replaceByArg + ")"
           : replaceByArg;
  }

  private static String longLiteralText(PsiElement context, String text) {
    if (context instanceof PsiLiteralExpression) {
      return text + "L";
    }
    else if (context instanceof PsiPrefixExpression expression) {
      return longLiteralText(expression.getOperand(), text);
    }
    return null;
  }

  private static @NonNls String getBoxedWrapper(PsiType from,
                                                PsiClassType to,
                                                @NotNull @NonNls String arg,
                                                TypeMigrationLabeler labeler,
                                                PsiElement context,
                                                @Nullable String tryType) {
    if (from instanceof PsiPrimitiveType) {
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
      final PsiClass threadLocalClass = resolveResult.getElement();
      LOG.assertTrue(threadLocalClass != null);
      final PsiTypeParameter[] typeParameters = threadLocalClass.getTypeParameters();
      if (typeParameters.length == 1) {
        final PsiType initial = resolveResult.getSubstitutor().substitute(typeParameters[0]);
        final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(initial);
        if (unboxedInitialType != null) {
          if (tryType != null) {
            final PsiType exprType = labeler.getTypeEvaluator().evaluateType(
              JavaPsiFacade.getElementFactory(threadLocalClass.getProject()).createExpressionFromText(tryType, context));
            if (exprType != null && unboxedInitialType.isAssignableFrom(exprType)) {
              return coerceType(arg, from, to, context);
            }
          }
          return PsiUtil.isLanguageLevel5OrHigher(context)
                 ? "(" + unboxedInitialType.getCanonicalText() + ")(" + arg + ")"
                 : "new " + initial.getCanonicalText() + "((" + unboxedInitialType.getCanonicalText() + ")(" + arg + "))";
        }
      }
    }
    return coerceType(arg, from, to, context);
  }

  private static final class WrappingWithInnerClassOrLambdaDescriptor extends ArrayInitializerAwareConversionDescriptor {
    private final List<? extends PsiVariable> myVariablesToMakeFinal;

    private WrappingWithInnerClassOrLambdaDescriptor(@NonNls String stringToReplace,
                                                     @NonNls String replaceByString,
                                                     PsiExpression expression,
                                                     @NotNull List<? extends PsiVariable> toMakeFinal) {
      super(stringToReplace, replaceByString, expression);
      myVariablesToMakeFinal = toMakeFinal;
    }

    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
      PsiExpression replaced = super.replace(expression, evaluator);
      boolean atLeastJava8 = PsiUtil.isLanguageLevel8OrHigher(replaced);
      for (PsiVariable var : myVariablesToMakeFinal) {
        if (!atLeastJava8 || !HighlightControlFlowUtil.isEffectivelyFinal(var, replaced, null)) {
          VariableAccessFromInnerClassFix.fixAccess(var, replaced);
        }
      }
      return replaced;
    }
  }
}