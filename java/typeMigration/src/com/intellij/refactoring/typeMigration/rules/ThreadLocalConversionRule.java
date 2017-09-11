/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableAccessFromInnerClassFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
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

public class ThreadLocalConversionRule extends TypeConversionRule {
  private static final Logger LOG = Logger.getInstance(ThreadLocalConversionRule.class);


  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from,
                                                 PsiType to,
                                                 PsiMember member,
                                                 PsiExpression context,
                                                 TypeMigrationLabeler labeler) {
    if (to instanceof PsiClassType && isThreadLocalTypeMigration(from, (PsiClassType)to, context)) {
      return findDirectConversion(context, to, from, labeler);
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
          return TypeConversionUtil.isAssignable(from, PsiUtil.captureToplevelWildcards(toTypeParameterValue, context));
        }
      }
      return !PsiUtil.isLanguageLevel5OrHigher(context);
    }
    return false;
  }

  @Nullable
  public static TypeConversionDescriptor findDirectConversion(PsiElement context, PsiType to, PsiType from, TypeMigrationLabeler labeler) {
    final PsiClass toTypeClass = PsiUtil.resolveClassInType(to);
    LOG.assertTrue(toTypeClass != null);

    if (context instanceof PsiArrayAccessExpression) {
      return new TypeConversionDescriptor("$qualifier$[$val$]", "$qualifier$.get()[$val$]");
    }
    final PsiElement parent = context.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final IElementType operationSign = ((PsiAssignmentExpression)parent).getOperationTokenType();
      if (operationSign == JavaTokenType.EQ) {
        return new TypeConversionDescriptor("$qualifier$ = $val$", "$qualifier$.set(" + toBoxed("$val$", from, context)+")", (PsiAssignmentExpression)parent);
      }
    }

    if (context instanceof PsiReferenceExpression) {
      final PsiExpression qualifierExpression = ((PsiReferenceExpression)context).getQualifierExpression();
      final PsiExpression expression = context.getParent() instanceof PsiMethodCallExpression && qualifierExpression != null
                                       ? qualifierExpression
                                       : (PsiExpression)context;
      return new TypeConversionDescriptor("$qualifier$", toPrimitive("$qualifier$.get()", from, context), expression);
    }
    else if (context instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)context;
      final String sign = binaryExpression.getOperationSign().getText();
      return new TypeConversionDescriptor("$qualifier$" + sign + "$val$", toPrimitive("$qualifier$.get()", from, context) + " " + sign + " $val$");
    }

    if (parent instanceof PsiVariable && ((PsiVariable)parent).getInitializer() == context) {
      return wrapWithNewExpression(to, from, (PsiExpression)context);
    }

    if (parent instanceof PsiExpressionStatement) {
      if (context instanceof PsiPostfixExpression) {
        final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)context;
        final String sign = postfixExpression.getOperationSign().getText();

        return new TypeConversionDescriptor("$qualifier$" + sign, "$qualifier$.set(" +
                                                                  getBoxedWrapper(from, to, toPrimitive("$qualifier$.get()", from, context) + " " + sign.charAt(0) + " 1",
                                                                                  labeler, context, postfixExpression.getOperand().getText() +
                                                                                           sign.charAt(0) +
                                                                                           " 1") +
                                                                  ")");
      }
      else if (context instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)context;
        final PsiJavaToken operationSign = ((PsiPrefixExpression)context).getOperationSign();
        if (operationSign.getTokenType() == JavaTokenType.EXCL) {
          return new TypeConversionDescriptor("!$qualifier$", "!$qualifier$.get()");
        }
        final String sign = operationSign.getText();
        final PsiExpression operand = prefixExpression.getOperand();
        return new TypeConversionDescriptor(sign + "$qualifier$", "$qualifier$.set(" +
                                                                  getBoxedWrapper(from, to, toPrimitive("$qualifier$.get()", from, context) + " " + sign.charAt(0) + " 1",
                                                                                  labeler, context, operand != null ? operand.getText() +
                                                                                                             sign.charAt(0) +
                                                                                                             " 1" : null) +
                                                                  ")");
      }
      else if (context instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)context;
        final PsiJavaToken signToken = assignmentExpression.getOperationSign();
        final IElementType operationSign = signToken.getTokenType();
        final String sign = signToken.getText();
        final PsiExpression lExpression = assignmentExpression.getLExpression();
        if (operationSign == JavaTokenType.EQ) {
          if (lExpression instanceof PsiReferenceExpression) {
            final PsiElement element = ((PsiReferenceExpression)lExpression).resolve();
            if (element instanceof PsiVariable && ((PsiVariable)element).hasModifierProperty(PsiModifier.FINAL)) {
              return wrapWithNewExpression(to, from, ((PsiAssignmentExpression)context).getRExpression());
            }
          }
          return new TypeConversionDescriptor("$qualifier$ = $val$", "$qualifier$.set(" +
                                                                     toBoxed("$val$", from, context) +
                                                                     ")");
        }
        else {
          final PsiExpression rExpression = assignmentExpression.getRExpression();
          return new TypeConversionDescriptor("$qualifier$" + sign + "$val$", "$qualifier$.set(" +
                                                                              getBoxedWrapper(from, to, toPrimitive("$qualifier$.get()", from, context) +
                                                                                                        " " + sign.charAt(0) +
                                                                                                        " $val$", labeler, context,
                                                                                              rExpression != null
                                                                                              ? lExpression
                                                                                                .getText() +
                                                                                                sign.charAt(0) +
                                                                                                rExpression.getText()
                                                                                              : null) +
                                                                              ")");
        }
      }
    }
    return null;
  }

  public static TypeConversionDescriptor wrapWithNewExpression(PsiType to, PsiType from, PsiExpression initializer) {
    final String boxedTypeName = from instanceof PsiPrimitiveType ? ((PsiPrimitiveType)from).getBoxedTypeName() : from.getCanonicalText();
    List<PsiVariable> toMakeFinal = TypeConversionRuleUtil.getVariablesToMakeFinal(initializer);
    if (toMakeFinal == null) return null;
    return new WrappingWithInnerClassOrLambdaDescriptor("$qualifier$",
                                                        createThreadLocalInitializerReplacement(to, from, initializer, boxedTypeName),
                                                        initializer,
                                                        toMakeFinal);
  }

  private static String createThreadLocalInitializerReplacement(PsiType to,
                                                                  PsiType from,
                                                                  PsiExpression initializer,
                                                                  String boxedTypeName) {
    if (PsiUtil.isLanguageLevel8OrHigher(initializer)) {
      return "java.lang.ThreadLocal.withInitial(() -> $qualifier$)";
    }
    return "new " +
           to.getCanonicalText() +
           "() {\n" +
           "@Override\n" +
           "protected " +
           boxedTypeName +
           " initialValue() {\n" +
           "  return " +
           (PsiUtil.isLanguageLevel5OrHigher(initializer)
                          ? initializer.getText()
                          : (from instanceof PsiPrimitiveType ? "new " +
                                                                ((PsiPrimitiveType)from).getBoxedTypeName() +
                                                                "($qualifier$)" : "$qualifier$")) +
           ";\n" +
           "}\n" +
           "}";
  }

  private static String toPrimitive(String replaceByArg, PsiType from, PsiElement context) {
    return PsiUtil.isLanguageLevel5OrHigher(context)
           ? replaceByArg
           : from instanceof PsiPrimitiveType ? "((" +
                                                ((PsiPrimitiveType)from).getBoxedTypeName() +
                                                ")" +
                                                replaceByArg +
                                                ")." +
                                                from.getCanonicalText() +
                                                "Value()" : "((" + from.getCanonicalText() + ")" + replaceByArg + ")";
  }

  private static String toBoxed(String replaceByArg, PsiType from, PsiElement context) {
    return PsiUtil.isLanguageLevel5OrHigher(context)
           ? replaceByArg
           : from instanceof PsiPrimitiveType ? "new " + ((PsiPrimitiveType)from).getBoxedTypeName() +
                                                "(" +
                                                replaceByArg +
                                                ")"
                                                : replaceByArg;
  }

  private static String getBoxedWrapper(final PsiType from,
                                        final PsiType to,
                                        @NotNull String arg,
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
              return toBoxed(arg, from, context);
            }
          }
          return "new " + initial.getCanonicalText() + "((" + unboxedInitialType.getCanonicalText() + ")(" + arg + "))";
        }
      }
    }
    return toBoxed(arg, from, context);
  }

  private static class WrappingWithInnerClassOrLambdaDescriptor extends ArrayInitializerAwareConversionDescriptor {
    private final List<PsiVariable> myVariablesToMakeFinal;

    private WrappingWithInnerClassOrLambdaDescriptor(@NonNls final String stringToReplace,
                                                     @NonNls final String replaceByString,
                                                     final PsiExpression expression,
                                                     @NotNull List<PsiVariable> toMakeFinal) {
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