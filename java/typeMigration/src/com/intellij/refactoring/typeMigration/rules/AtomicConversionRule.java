// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class AtomicConversionRule extends TypeConversionRule {
  private static final Logger LOG = Logger.getInstance(AtomicConversionRule.class);


  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from,
                                                 PsiType to,
                                                 PsiMember member,
                                                 PsiExpression context,
                                                 TypeMigrationLabeler labeler) {
    if (to instanceof PsiClassType) {
      AtomicConversionType type = AtomicConversionType.getConversionType(from, (PsiClassType)to, context);
      if (type != null) {
        return findDirectConversion(context, to, from, type, labeler);
      }
    }
    if (from instanceof PsiClassType && AtomicConversionType.getConversionType(to, (PsiClassType)from, context) != null) {
      return findReverseConversion(context);
    }
    return null;
  }

  @Override
  public boolean shouldConvertNullInitializer(PsiType from, PsiType to, PsiExpression context) {
    return to instanceof PsiClassType && AtomicConversionType.getConversionType(from, (PsiClassType)to, context) != null;
  }

  @Nullable
  private static TypeConversionDescriptor findDirectConversion(PsiElement context,
                                                               PsiType to,
                                                               PsiType from,
                                                               AtomicConversionType type,
                                                               TypeMigrationLabeler labeler) {
    final PsiClass toTypeClass = PsiUtil.resolveClassInType(to);
    LOG.assertTrue(toTypeClass != null);
    final String qualifiedName = toTypeClass.getQualifiedName();
    if (context instanceof PsiParenthesizedExpression) {
      context = PsiUtil.skipParenthesizedExprDown((PsiExpression)context);
    }
    if (qualifiedName != null) {
      if (qualifiedName.equals(AtomicInteger.class.getName()) || qualifiedName.equals(AtomicLong.class.getName())) {

        if (context instanceof PsiPostfixExpression) {
          final IElementType operationSign = ((PsiPostfixExpression)context).getOperationTokenType();
          if (operationSign == JavaTokenType.MINUSMINUS) {
            return new TypeConversionDescriptor("$qualifier$--", "$qualifier$.getAndDecrement()");
          }
          if (operationSign == JavaTokenType.PLUSPLUS) {
            return new TypeConversionDescriptor("$qualifier$++", "$qualifier$.getAndIncrement()");
          }

        }
        else if (context instanceof PsiPrefixExpression) {
          final IElementType operationSign = ((PsiPrefixExpression)context).getOperationTokenType();
          if (operationSign == JavaTokenType.MINUSMINUS) {
            return new TypeConversionDescriptor("--$qualifier$", "$qualifier$.decrementAndGet()");
          }
          if (operationSign == JavaTokenType.PLUSPLUS) {
            return new TypeConversionDescriptor("++$qualifier$", "$qualifier$.incrementAndGet()");
          }

          if (operationSign == JavaTokenType.MINUS && 
              !(context.getParent() instanceof PsiAssignmentExpression)) {
            return wrapWithNewExpression(to, from, (PsiExpression)context, context, type);
          }

        }
        else if (context instanceof PsiAssignmentExpression) {
          final PsiJavaToken signToken = ((PsiAssignmentExpression)context).getOperationSign();
          final IElementType operationSign = signToken.getTokenType();
          final String sign = signToken.getText();
          if (operationSign == JavaTokenType.PLUSEQ || operationSign == JavaTokenType.MINUSEQ) {
            return new TypeConversionDescriptor("$qualifier$ " + sign + " $val$",
                                                "$qualifier$.addAndGet(" + (operationSign == JavaTokenType.MINUSEQ ? "-($val$))" : "$val$)")) {
              @Override
              public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
                final PsiMethodCallExpression result = (PsiMethodCallExpression)super.replace(expression, evaluator);
                final PsiExpression argument = result.getArgumentList().getExpressions()[0];
                if (argument instanceof PsiPrefixExpression) {
                  final PsiExpression operand = ((PsiPrefixExpression)argument).getOperand();
                  final PsiExpression striped = PsiUtil.skipParenthesizedExprDown(operand);
                  if (striped != null && operand != striped) {
                    operand.replace(striped);
                  }
                }
                return result;
              }
            };
          }
        }
        else if (context instanceof PsiLiteralExpression && !(context.getParent() instanceof PsiAssignmentExpression)) {
          return wrapWithNewExpression(to, from, (PsiExpression)context, context, type);
        }
      }
      else if (qualifiedName.equals(AtomicIntegerArray.class.getName()) || qualifiedName.equals(AtomicLongArray.class.getName())) {
        PsiElement parentExpression = context.getParent();
        if (parentExpression instanceof PsiPostfixExpression) {
          final IElementType operationSign = ((PsiPostfixExpression)parentExpression).getOperationTokenType();
          if (operationSign == JavaTokenType.MINUSMINUS) {
            return new TypeConversionDescriptor("$qualifier$[$idx$]--", "$qualifier$.getAndDecrement($idx$)",
                                                (PsiExpression)parentExpression);
          }
          if (operationSign == JavaTokenType.PLUSPLUS) {
            return new TypeConversionDescriptor("$qualifier$[$idx$]++", "$qualifier$.getAndIncrement($idx$)",
                                                (PsiExpression)parentExpression);
          }

        }
        else if (parentExpression instanceof PsiPrefixExpression) {
          final IElementType operationSign = ((PsiPrefixExpression)parentExpression).getOperationTokenType();
          if (operationSign == JavaTokenType.MINUSMINUS) {
            return new TypeConversionDescriptor("--$qualifier$[$idx$]", "$qualifier$.decrementAndGet($idx$)",
                                                (PsiExpression)parentExpression);
          }
          if (operationSign == JavaTokenType.PLUSPLUS) {
            return new TypeConversionDescriptor("++$qualifier$[$idx$]", "$qualifier$.incrementAndGet($idx$)",
                                                (PsiExpression)parentExpression);
          }

        }
        else if (parentExpression instanceof PsiAssignmentExpression) {
          final PsiJavaToken signToken = ((PsiAssignmentExpression)parentExpression).getOperationSign();
          final IElementType operationSign = signToken.getTokenType();
          final String sign = signToken.getText();
          if (operationSign == JavaTokenType.PLUSEQ || operationSign == JavaTokenType.MINUSEQ) {
            return new TypeConversionDescriptor("$qualifier$[$idx$] " + sign + " $val$", "$qualifier$.getAndAdd($idx$, " +
                                                                                         (operationSign == JavaTokenType.MINUSEQ
                                                                                          ? "-"
                                                                                          : "") +
                                                                                         "($val$))", (PsiExpression)parentExpression);
          }
        }
      }
    }
    return from instanceof PsiArrayType
           ? findDirectConversionForAtomicReferenceArray(context, to, from, type)
           : findDirectConversionForAtomicReference(context, to, from, type, labeler);
  }

  @Nullable
  private static TypeConversionDescriptor findDirectConversionForAtomicReference(PsiElement context,
                                                                                 PsiType to,
                                                                                 PsiType from,
                                                                                 AtomicConversionType type,
                                                                                 TypeMigrationLabeler labeler) {
    final PsiElement parent = context.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final IElementType operationSign = ((PsiAssignmentExpression)parent).getOperationTokenType();
      if (operationSign == JavaTokenType.EQ) {
        boolean rightInfected = ((PsiAssignmentExpression)parent).getLExpression() == context;
        String replacement = rightInfected ? "$qualifier$ = $val$.get()" : "$qualifier$.set($val$)";
        return new TypeConversionDescriptor("$qualifier$ = $val$", replacement, (PsiAssignmentExpression)parent);
      }
    }

    if (context instanceof PsiReferenceExpression refExpr) {
      final PsiExpression qualifierExpression = refExpr.getQualifierExpression();
      final PsiExpression expression = context.getParent() instanceof PsiMethodCallExpression && qualifierExpression != null
                                       ? qualifierExpression : refExpr;
      if (expression instanceof PsiReferenceExpression && to.equals(labeler.getTypeEvaluator().getType(expression))) {
        return new TypeConversionDescriptor("$qualifier$", "$qualifier$.get()", expression);
      }
    }
    else if (context instanceof PsiAssignmentExpression assignment) {
      final PsiJavaToken signToken = assignment.getOperationSign();
      final IElementType operationSign = signToken.getTokenType();
      final String sign = signToken.getText();
      boolean voidContext = ExpressionUtils.isVoidContext(assignment);
      if (operationSign == JavaTokenType.EQ) {
        if (!voidContext) return null;
        final PsiExpression lExpression = assignment.getLExpression();
        if (lExpression instanceof PsiReferenceExpression) {
          final PsiElement element = ((PsiReferenceExpression)lExpression).resolve();
          if (element instanceof PsiVariable && ((PsiVariable)element).hasModifierProperty(PsiModifier.FINAL)) {
            return wrapWithNewExpression(to, from, assignment.getRExpression(), element, type);
          }
        }
        return new TypeConversionDescriptor("$qualifier$ = $val$", "$qualifier$.set($val$)");
      }
      if (PsiUtil.isLanguageLevel8OrHigher(context) && !PsiTypes.booleanType().equals(from)) {
        final String name =
          JavaCodeStyleManager.getInstance(context.getProject()).suggestUniqueVariableName("v", context, false);
        return new TypeConversionDescriptor("$qualifier$" + sign + "$val$",
                                            "$qualifier$.updateAndGet(" +
                                            name +
                                            " -> " +
                                            getBoxedWrapper(from, to, name + " " + sign.charAt(0) + " $val$)"));
      }
      if (voidContext) {
        return new TypeConversionDescriptor("$qualifier$" + sign + "$val$",
                                            "$qualifier$.set(" +
                                            getBoxedWrapper(from, to, "$qualifier$.get() " + sign.charAt(0) + " $val$") +
                                            ")");
      }
    }
    else if (context instanceof PsiPostfixExpression) {
      final String sign = ((PsiPostfixExpression)context).getOperationSign().getText();
      return new TypeConversionDescriptor("$qualifier$" + sign, "$qualifier$.getAndSet(" +
                                                                getBoxedWrapper(from, to, "$qualifier$.get() " + sign.charAt(0) + " 1") +
                                                                ")");
    }
    else if (context instanceof PsiPrefixExpression) {
      final PsiJavaToken operationSign = ((PsiPrefixExpression)context).getOperationSign();
      if (operationSign.getTokenType() == JavaTokenType.EXCL) {
        return new TypeConversionDescriptor("!$qualifier$", "!$qualifier$.get()");
      }
      final String sign = operationSign.getText();
      return new TypeConversionDescriptor(sign + "$qualifier$", "$qualifier$.set(" +  //todo reject?
                                                                getBoxedWrapper(from, to, "$qualifier$.get() " + sign.charAt(0) + " 1") +
                                                                ")");
    }

    if (parent instanceof PsiVariable) {
      return wrapWithNewExpression(to, from, null, parent, type);
    }
    return null;
  }

  private static TypeConversionDescriptor wrapWithNewExpression(PsiType to,
                                                                PsiType from,
                                                                @Nullable PsiExpression expression,
                                                                PsiElement context,
                                                                @NotNull AtomicConversionType type) {
    final String typeText = PsiDiamondTypeUtil.getCollapsedType(to, context);
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
    final PsiClass atomicClass = resolveResult.getElement();
    LOG.assertTrue(atomicClass != null);
    final PsiTypeParameter[] typeParameters = atomicClass.getTypeParameters();
    if (typeParameters.length == 1) {
      final PsiType initial = resolveResult.getSubstitutor().substitute(typeParameters[0]);
      final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(initial);
      if (unboxedInitialType != null) {
        if (from instanceof PsiPrimitiveType) {
          final PsiClassType boxedFromType = ((PsiPrimitiveType)from).getBoxedType(atomicClass);
          LOG.assertTrue(boxedFromType != null);
          if (!TypeConversionUtil.isAssignable(initial, boxedFromType)) {
            return new AtomicConstructorConversionDescriptor("$val$",
                                                             "new " + typeText + "((" + unboxedInitialType.getCanonicalText() + ")$val$)",
                                                             expression,
                                                             type);
          }
        }
      }
    }
    return new AtomicConstructorConversionDescriptor("$val$", "new " + typeText + "($val$)", expression, type);
  }

  @Nullable
  private static TypeConversionDescriptor findDirectConversionForAtomicReferenceArray(PsiElement context,
                                                                                      PsiType to,
                                                                                      PsiType from,
                                                                                      AtomicConversionType type) {
    LOG.assertTrue(from instanceof PsiArrayType);
    from = ((PsiArrayType)from).getComponentType();
    final PsiElement parent = context.getParent();
    final PsiElement parentParent = parent.getParent();

    if (context instanceof PsiReferenceExpression && ExpressionUtils.getArrayFromLengthExpression((PsiReferenceExpression)context) != null) {
      return new TypeConversionDescriptor("$qualifier$.length", "$qualifier$.length()");
    }
    if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      final IElementType operationSign = assignmentExpression.getOperationTokenType();
      final String sign = assignmentExpression.getOperationSign().getText();
      if (context instanceof PsiArrayAccessExpression) {
        if (parentParent instanceof PsiExpressionStatement) {
          if (assignmentExpression.getLExpression() == context) {
            if (operationSign == JavaTokenType.EQ) {
              return new TypeConversionDescriptor("$qualifier$[$idx$] = $val$", "$qualifier$.set($idx$, $val$)", assignmentExpression);
            }
            else {
              return new TypeConversionDescriptor("$qualifier$[$idx$]" + sign + "$val$",
                                                  "$qualifier$.set($idx$, " + getBoxedWrapper(from, to, "$qualifier$.get($idx$) " + sign.charAt(0) + " $val$") + ")",
                                                  assignmentExpression);
            }
          }
        } //else should be a conflict
      }
      else {
        final PsiExpression rExpression = assignmentExpression.getRExpression();
        if (rExpression == context && operationSign == JavaTokenType.EQ) {   //array = new T[l];
          return wrapWithNewExpression(to, from, rExpression, context, type);
        }
      }
    } else if (parent instanceof PsiVariable) {
      if (((PsiVariable)parent).getInitializer() == context) {
        return wrapWithNewExpression(to, from, (PsiExpression)context, context, type);
      }
    }

    if (parentParent instanceof PsiExpressionStatement) {
      if (parent instanceof PsiPostfixExpression) {
        final String sign = ((PsiPostfixExpression)parent).getOperationSign().getText();
        return new TypeConversionDescriptor("$qualifier$[$idx$]" + sign, "$qualifier$.getAndSet($idx$, " +
                                                                         getBoxedWrapper(from, to,
                                                                                         "$qualifier$.get($idx$) " + sign.charAt(0) + " 1") +
                                                                         ")", (PsiExpression)parent);
      }
      else if (parent instanceof PsiPrefixExpression) {
        final String sign = ((PsiPrefixExpression)parent).getOperationSign().getText();
        return new TypeConversionDescriptor(sign + "$qualifier$[$idx$]", "$qualifier$.set($idx$, " +
                                                                         getBoxedWrapper(from, to,
                                                                                         "$qualifier$.get($idx$) " + sign.charAt(0) + " 1") +
                                                                         ")", (PsiExpression)parent);
      }
      else if (parent instanceof PsiBinaryExpression) {
        final String sign = ((PsiBinaryExpression)parent).getOperationSign().getText();
        return new TypeConversionDescriptor("$qualifier$[$idx$]" + sign + "$val$", "$qualifier$.set($idx$, " +
                                                                                   getBoxedWrapper(from, to, "$qualifier$.get($idx$) " +
                                                                                                             sign +
                                                                                                             " $val$)") +
                                                                                   ")", (PsiExpression)parent);
      }
    }

    if (context instanceof PsiArrayAccessExpression) {
      return new TypeConversionDescriptor("$qualifier$[$idx$]", "$qualifier$.get($idx$)", (PsiExpression)context);
    }
    return null;
  }

  private static @NonNls String getBoxedWrapper(final PsiType from, final PsiType to, @NotNull @NonNls String arg) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
    final PsiClass atomicClass = resolveResult.getElement();
    LOG.assertTrue(atomicClass != null);
    final PsiTypeParameter[] typeParameters = atomicClass.getTypeParameters();
    if (typeParameters.length == 1) {
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      LOG.assertTrue(substitutor.isValid());
      final PsiType initial = substitutor.substitute(typeParameters[0]);
      final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(initial);
      if (unboxedInitialType != null) {
        if (from instanceof PsiPrimitiveType) {
          final PsiClassType boxedFromType = ((PsiPrimitiveType)from).getBoxedType(atomicClass);
          LOG.assertTrue(boxedFromType != null);
          return "new " + initial.getPresentableText() + "((" + unboxedInitialType.getCanonicalText() + ")(" + arg + "))";
        }
      }
    }
    return arg;
  }

  @Nullable
  private static TypeConversionDescriptor findReverseConversion(PsiElement context) {
    if (context instanceof PsiReferenceExpression) {
      if (context.getParent() instanceof PsiMethodCallExpression) {
        return findReverseConversionForMethodCall(context);
      }
    }
    else if (context instanceof PsiNewExpression) {
      return new TypeConversionDescriptor("new $type$($qualifier$)", "$qualifier$");
    }
    else if (context instanceof PsiMethodCallExpression) {
      return findReverseConversionForMethodCall(((PsiMethodCallExpression)context).getMethodExpression());
    }
    return null;
  }

  @Nullable
  private static TypeConversionDescriptor findReverseConversionForMethodCall(PsiElement context) {
    final PsiElement resolved = ((PsiReferenceExpression)context).resolve();
    if (resolved instanceof PsiMethod method) {
      final int parametersCount = method.getParameterList().getParametersCount();
      final String resolvedName = method.getName();
      if (Comparing.strEqual(resolvedName, "get")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.get()", "$qualifier$") :
               new TypeConversionDescriptor("$qualifier$.get($idx$)", "$qualifier$[$idx$]");
      }
      else if (Comparing.strEqual(resolvedName, "set")) {
        return parametersCount == 1 ?
               new TypeConversionDescriptor("$qualifier$.set($val$)", "$qualifier$ = $val$") :
               new TypeConversionDescriptor("$qualifier$.set($idx$, $val$)", "$qualifier$[$idx$] = $val$");
      }
      else if (Comparing.strEqual(resolvedName, "addAndGet")) {
        return parametersCount == 1 ?
               new TypeConversionDescriptor("$qualifier$.addAndGet($delta$)", "$qualifier$ + $delta$") :
               new TypeConversionDescriptor("$qualifier$.addAndGet($idx$, $delta$)", "$qualifier$[$idx$] + $delta$");
      }
      else if (Comparing.strEqual(resolvedName, "incrementAndGet")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.incrementAndGet()", "++$qualifier$") :
               new TypeConversionDescriptor("$qualifier$.incrementAndGet($idx$)", "++$qualifier$[$idx$]");
      }
      else if (Comparing.strEqual(resolvedName, "decrementAndGet")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.decrementAndGet()", "--$qualifier$") :
               new TypeConversionDescriptor("$qualifier$.decrementAndGet($idx$)", "--$qualifier$[$idx$]");
      }
      else if (Comparing.strEqual(resolvedName, "getAndIncrement")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.getAndIncrement()", "$qualifier$++") :
               new TypeConversionDescriptor("$qualifier$.getAndIncrement($idx$)", "$qualifier$[$idx$]++");
      }
      else if (Comparing.strEqual(resolvedName, "getAndDecrement")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.getAndDecrement()", "$qualifier$--") :
               new TypeConversionDescriptor("$qualifier$.getAndDecrement($idx$)", "$qualifier$[$idx$]--");
      }
      else if (Comparing.strEqual(resolvedName, "getAndAdd")) {
        return parametersCount == 1?
               new TypeConversionDescriptor("$qualifier$.getAndAdd($val$)", "$qualifier$ += $val$") :
               new TypeConversionDescriptor("$qualifier$.getAndAdd($idx$, $val$)", "$qualifier$[$idx$] += $val$");
      }
      else if (Comparing.strEqual(resolvedName, "getAndSet")) {
        return parametersCount == 1 ?
               new TypeConversionDescriptor("$qualifier$.getAndSet($val$)", "$qualifier$ = $val$") :
               new TypeConversionDescriptor("$qualifier$.getAndSet($idx$, $val$)", "$qualifier$[$idx$] = $val$");
      }
    }
    return null;
  }
}
