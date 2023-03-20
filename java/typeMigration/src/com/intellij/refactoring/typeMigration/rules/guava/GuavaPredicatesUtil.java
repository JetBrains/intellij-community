// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
final class GuavaPredicatesUtil {
  private static final Logger LOG = Logger.getInstance(GuavaPredicatesUtil.class);

  private static final Set<String> PREDICATES_AND_OR = ContainerUtil.newHashSet("or", "and");
  private static final String PREDICATES_NOT = "not";
  public static final Set<String> PREDICATES_METHOD_NAMES =
    ContainerUtil.newHashSet("alwaysTrue", "alwaysFalse", "isNull", "notNull", "equalTo", "not", "or", "and");

  @Nullable
  static TypeConversionDescriptorBase tryConvertIfPredicates(PsiMethod method, PsiExpression context) {
    final String name = method.getName();
    switch (name) {
      case "alwaysTrue", "alwaysFalse" -> {
        return createConstantPredicate(name, name.contains("True"));
      }
      case "isNull", "notNull" -> {
        final String operation = name.equals("isNull") ? "==" : "!=";
        return new TypeConversionDescriptorWithLocalVariable(name, "$x$ -> $x$" + operation + " null");
      }
      case "equalTo" -> {
        return new TypeConversionDescriptorWithLocalVariable("equalTo", "$x$ -> java.util.Objects.equals($x$, $v$)");
      }
    }
    if (!isConvertablePredicatesMethod(method, (PsiMethodCallExpression)context)) return null;
    if (((PsiMethodCallExpression)context).getArgumentList().isEmpty()) {
      return createConstantPredicate(name, name.equals("and"));
    }
    if (PREDICATES_AND_OR.contains(name) && canMigrateAndOrOr((PsiMethodCallExpression)context)) {
      return new AndOrOrConversionDescriptor(GuavaConversionUtil.addTypeParameters(GuavaLambda.PREDICATE.getJavaAnalogueClassQName(), context.getType(), context));
    }
    else if (PREDICATES_NOT.equals(name)) {
      return new NotConversionDescriptor(GuavaConversionUtil.addTypeParameters(GuavaLambda.PREDICATE.getJavaAnalogueClassQName(), context.getType(), context));
    }
    return null;
  }

  @NotNull
  private static TypeConversionDescriptorWithLocalVariable createConstantPredicate(String methodName, boolean value) {
    return new TypeConversionDescriptorWithLocalVariable(methodName, "$x$ -> " + value);
  }

  private static class TypeConversionDescriptorWithLocalVariable extends TypeConversionDescriptor {
    private final String myReplaceByStringTemplate;

    TypeConversionDescriptorWithLocalVariable(@NlsSafe String methodName, @NonNls String replaceByString) {
      super("'_Predicates?." + methodName + "(" + (methodName.equals("equalTo") ? "$v$" : "") + ")", null);
      myReplaceByStringTemplate = replaceByString;
    }

    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
      final String chosenName = FluentIterableConversionUtil.chooseName(expression, getIntroducedVariableType(expression));
      setReplaceByString(StringUtil.replace(myReplaceByStringTemplate, "$x$", chosenName));
      return super.replace(expression, evaluator);
    }

    private static PsiType getIntroducedVariableType(PsiExpression expression) {
      final PsiType type = expression.getType();
      if (type instanceof PsiClassType) {
        final PsiType[] parameters = ((PsiClassType)type).getParameters();
        if (parameters.length == 1) {
          final PsiType parameter = parameters[0];
          if (parameter instanceof PsiClassType) {
            return parameter;
          }
        }
      }
      return null;
    }
  }


  public static boolean isConvertablePredicatesMethod(@NotNull PsiMethod method, PsiMethodCallExpression context) {
    if (method.getParameterList().getParametersCount() == 1) {
      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      final PsiClass psiClass = PsiTypesUtil.getPsiClass(parameter.getType().getDeepComponentType());
      if (psiClass == null || CommonClassNames.JAVA_LANG_ITERABLE.equals(psiClass.getQualifiedName())) {
        return false;
      }
    }
    final PsiExpression[] expressions = context.getArgumentList().getExpressions();
    return !(expressions.length == 1 && expressions[0].getType() instanceof PsiArrayType);
  }

  private static boolean canMigrateAndOrOr(PsiMethodCallExpression expr) {
    final PsiMethod method = expr.resolveMethod();
    if (method == null) return false;
    final PsiParameterList parameters = method.getParameterList();
    if (parameters.getParametersCount() != 1) {
      return !parameters.isEmpty();
    }
    final PsiParameter parameter = parameters.getParameters()[0];
    final PsiType type = parameter.getType();
    return type instanceof PsiEllipsisType;
  }

  private static class NotConversionDescriptor extends TypeConversionDescriptorBase {
    private final PsiType myTargetType;

    NotConversionDescriptor(PsiType targetType) {
      myTargetType = targetType;
    }

    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
      @NonNls String newExpressionString =
        GuavaConversionUtil.adjustLambdaContainingExpression(((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0], true, myTargetType, evaluator).getText() + ".negate()";

      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiMethodReferenceExpression) {
        expression = replaceTypeCast(expression, parent);
      }
      else if (!GuavaConversionUtil.isJavaLambda(parent, evaluator)) {
        newExpressionString += "::test";
      }
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
      PsiExpression convertedExpression =
        (PsiExpression)expression.replace(elementFactory.createExpressionFromText(newExpressionString, expression));
      convertedExpression = convertedExpression.getParent() instanceof PsiMethodReferenceExpression
                            ? (PsiExpression)convertedExpression.getParent().replace(convertedExpression)
                            : convertedExpression;
      final PsiElement maybeTypeCast = convertedExpression.getParent();
      if (maybeTypeCast instanceof PsiTypeCastExpression && RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)maybeTypeCast)) {
        convertedExpression = (PsiExpression)maybeTypeCast.replace(((PsiTypeCastExpression)maybeTypeCast).getOperand());
      }
      return convertedExpression;
    }
  }

  private static class AndOrOrConversionDescriptor extends TypeConversionDescriptorBase {
    private final PsiType myTargetType;

    AndOrOrConversionDescriptor(PsiType targetType) {
      myTargetType = targetType;
    }

    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
      final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
      if (arguments.length == 1) {
        return (PsiExpression)expression.replace(GuavaConversionUtil.adjustLambdaContainingExpression(arguments[0], true, myTargetType, evaluator));
      }
      LOG.assertTrue(arguments.length != 0);
      final @NonNls StringBuilder replaceBy = new StringBuilder();
      final String methodName = methodCall.getMethodExpression().getReferenceName();
      for (int i = 1; i < arguments.length; i++) {
        PsiExpression argument = arguments[i];
        replaceBy.append(".").append(methodName).append("(").append(GuavaConversionUtil.adjustLambdaContainingExpression(argument, false, myTargetType, evaluator).getText()).append(")");
      }
      replaceBy.insert(0, GuavaConversionUtil.adjustLambdaContainingExpression(arguments[0], true, myTargetType, evaluator).getText());
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiMethodReferenceExpression) {
        expression = replaceTypeCast(expression, parent);
      }
      else if (!GuavaConversionUtil.isJavaLambda(parent, evaluator)) {
        replaceBy.append("::test");
      }
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
      return (PsiExpression)expression.replace(elementFactory.createExpressionFromText(replaceBy.toString(), expression));
    }
  }

  private static PsiExpression replaceTypeCast(PsiExpression expression, PsiElement parent) {
    final PsiElement parParent = parent.getParent();
    if (parParent instanceof PsiTypeCastExpression) {
      final PsiTypeElement typeElement = ((PsiTypeCastExpression)parParent).getCastType();
      if (typeElement != null) {
        final PsiType type = typeElement.getType();
        final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
        if (aClass != null && GuavaLambda.PREDICATE.getClassQName().equals(aClass.getQualifiedName())) {
          expression = (PsiExpression)parParent.replace(expression);
        }
      }
    }
    return expression;
  }
}
