/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
/**
 * @author Dmitry Batkovich
 */
public class GuavaPredicateConversionRule extends BaseGuavaTypeConversionRule {
  private static final Logger LOG = Logger.getInstance(GuavaPredicateConversionRule.class);

  static final String GUAVA_PREDICATE = "com.google.common.base.Predicate";
  static final String JAVA_PREDICATE = "java.util.function.Predicate";

  public static final String GUAVA_PREDICATES_UTILITY = "com.google.common.base.Predicates";
  public static final Set<String> PREDICATES_AND_OR = ContainerUtil.newHashSet("or", "and");
  public static final String PREDIACTES_NOT = "not";


  @NotNull
  @Override
  protected Set<String> getAdditionalUtilityClasses() {
    return Collections.singleton(GUAVA_PREDICATES_UTILITY);
  }

  @Override
  protected void fillSimpleDescriptors(Map<String, TypeConversionDescriptorBase> descriptorsMap) {
    descriptorsMap.put("apply", new FunctionalInterfaceTypeConversionDescriptor("apply", "test"));
  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForVariableReference(@NotNull PsiReferenceExpression referenceExpression,
                                                                            @NotNull PsiVariable psiVariable, PsiExpression context) {
    return new FunctionalInterfaceTypeConversionDescriptor("apply", "test");
  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForMethod(PsiType from,
                                                                 PsiType to,
                                                                 @NotNull PsiMethod method,
                                                                 @NotNull String methodName,
                                                                 PsiExpression context,
                                                                 TypeMigrationLabeler labeler) {
    if (!(context instanceof PsiMethodCallExpression)) {
      return null;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass != null && GUAVA_PREDICATES_UTILITY.equals(aClass.getQualifiedName())) {
      if (!isConvertablePredicatesMethod(method)) return null;
      if (PREDICATES_AND_OR.contains(methodName) && canMigrateAndOrOr((PsiMethodCallExpression)context)) {
        return new AndOrOrConversionDescriptor(GuavaConversionUtil.addTypeParameters(JAVA_PREDICATE, context.getType(), context));
      }
      else if (PREDIACTES_NOT.equals(methodName)) {
        return new NotConversionDescriptor(GuavaConversionUtil.addTypeParameters(JAVA_PREDICATE, context.getType(), context));
      }
    }
    return new TypeConversionDescriptorBase() {
      @Override
      public PsiExpression replace(PsiExpression expression, TypeEvaluator evaluator) throws IncorrectOperationException {
        return (PsiExpression)expression.replace(JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(expression.getText() + "::test", expression));
      }
    };
  }

  public static boolean isConvertablePredicatesMethod(@NotNull PsiMethod method) {
    if (method.getParameterList().getParametersCount() == 1) {
      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      final PsiClass psiClass = PsiTypesUtil.getPsiClass(parameter.getType().getDeepComponentType());
      if (psiClass == null || CommonClassNames.JAVA_LANG_ITERABLE.equals(psiClass.getQualifiedName())) {
        return false;
      }
    }
    return true;
  }

  private static boolean canMigrateAndOrOr(PsiMethodCallExpression expr) {
    final PsiMethod method = expr.resolveMethod();
    if (method == null) return false;
    final PsiParameterList parameters = method.getParameterList();
    if (parameters.getParametersCount() != 1) {
      return parameters.getParametersCount() != 0;
    }
    final PsiParameter parameter = parameters.getParameters()[0];
    final PsiType type = parameter.getType();
    return type instanceof PsiEllipsisType;
  }

  private static class NotConversionDescriptor extends TypeConversionDescriptorBase {
    private final PsiType myTargetType;

    public NotConversionDescriptor(PsiType targetType) {
      myTargetType = targetType;
    }

    @Override
    public PsiExpression replace(PsiExpression expression, TypeEvaluator evaluator) throws IncorrectOperationException {
      String newExpressionString =
        adjust(((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0], true, myTargetType) + ".negate()";
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

    public AndOrOrConversionDescriptor(PsiType targetType) {
      myTargetType = targetType;
    }

    @Override
    public PsiExpression replace(PsiExpression expression, TypeEvaluator evaluator) throws IncorrectOperationException {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
      final String methodName = methodCall.getMethodExpression().getReferenceName();

      final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
      if (arguments.length == 1) {
        return (PsiExpression)expression.replace(arguments[0]);
      }
      LOG.assertTrue(arguments.length != 0);
      StringBuilder replaceBy = new StringBuilder();
      for (int i = 1; i < arguments.length; i++) {
        PsiExpression argument = arguments[i];
        replaceBy.append(".").append(methodName).append("(").append(adjust(argument, false, myTargetType)).append(")");
      }
      replaceBy.insert(0, adjust(arguments[0], true, myTargetType));
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
      return (PsiExpression)expression.replace(elementFactory.createExpressionFromText(replaceBy.toString(), expression));
    }

  }

  private static boolean isUnconverted(PsiType type) {
    final PsiClass predicateClass = PsiTypesUtil.getPsiClass(type);
    return predicateClass != null && !JAVA_PREDICATE.equals(predicateClass.getQualifiedName());
  }

  private static String adjust(PsiExpression expression, boolean insertTypeCase, PsiType targetType) {
    if (expression instanceof PsiFunctionalExpression) {
      if (insertTypeCase) {
        return "((" + targetType.getCanonicalText() + ")" + expression.getText() + ")";
      }
    }
    else if (expression instanceof PsiMethodCallExpression || expression instanceof PsiReferenceExpression) {
      if (isUnconverted(expression.getType())) {
        expression = (PsiExpression)expression.replace(JavaPsiFacade.getElementFactory(expression.getProject())
                                                                    .createExpressionFromText(expression.getText() + "::apply", expression));
        return adjust(expression, insertTypeCase, targetType);
      }
    }
    return expression.getText();
  }

  @NotNull
  @Override
  public String ruleFromClass() {
    return GUAVA_PREDICATE;
  }

  @NotNull
  @Override
  public String ruleToClass() {
    return JAVA_PREDICATE;
  }
}
