/*
 * User: anna
 * Date: 18-Aug-2009
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadLocalConversionRule extends TypeConversionRule {
  private static final Logger LOG = Logger.getInstance("#" + ThreadLocalConversionRule.class.getName());


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
    return new TypeConversionDescriptor("$qualifier$", "new " +
                                                       to.getCanonicalText() +
                                                       "() {\n" +
                                                       "@Override \n" +
                                                       "protected " +
                                                       boxedTypeName +
                                                       " initialValue() {\n" +
                                                       "  return " +
                                                       (PsiUtil.isLanguageLevel5OrHigher(initializer)
                                                        ? initializer.getText()
                                                        : (from instanceof PsiPrimitiveType ? "new " +
                                                                                              ((PsiPrimitiveType)from).getBoxedTypeName() +
                                                                                              "(" +
                                                                                              initializer.getText() +
                                                                                              ")" : initializer.getText())) +
                                                       ";\n" +
                                                       "}\n" +
                                                       "}", initializer);
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
          LOG.assertTrue(initial != null);
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


}