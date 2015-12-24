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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class GuavaPredicatesUtil {
  private static final Logger LOG = Logger.getInstance(GuavaPredicatesUtil.class);

  static final Set<String> PREDICATES_AND_OR = ContainerUtil.newHashSet("or", "and");
  static final String PREDICATES_NOT = "not";
  public static final Set<String> PREDICATES_METHOD_NAMES =
    ContainerUtil.newHashSet("alwaysTrue", "alwaysFalse", "isNull", "notNull", "equalTo", "not", "or", "and");

  @Nullable
  static TypeConversionDescriptorBase tryConvertIfPredicates(PsiMethod method, PsiExpression context) {
    final String name = method.getName();
    if (name.equals("alwaysTrue") || name.equals("alwaysFalse")) {
      return new TypeConversionDescriptorWithLocalVariable(name, "$x$ -> " + name.contains("True") + "");
    }
    else if (name.equals("isNull") || name.equals("notNull")) {
      final String operation = name.equals("isNull") ? "==" : "!=";
      return new TypeConversionDescriptorWithLocalVariable(name, "$x$ -> $x$" + operation + " null");
    }
    else if (name.equals("equalTo")) {
      return new TypeConversionDescriptorWithLocalVariable("equalTo", "$x$ -> java.util.Objects.equals($x$, $v$)");
    }
    if (!isConvertablePredicatesMethod(method)) return null;
    if (PREDICATES_AND_OR.contains(name) && canMigrateAndOrOr((PsiMethodCallExpression)context)) {
      return new AndOrOrConversionDescriptor(GuavaConversionUtil.addTypeParameters(GuavaPredicateConversionRule.JAVA_PREDICATE, context.getType(), context));
    }
    else if (PREDICATES_NOT.equals(name)) {
      return new NotConversionDescriptor(GuavaConversionUtil.addTypeParameters(GuavaPredicateConversionRule.JAVA_PREDICATE, context.getType(), context));
    }
    return null;
  }

  private static class TypeConversionDescriptorWithLocalVariable extends TypeConversionDescriptor {
    private final String myReplaceByStringTemplate;

    TypeConversionDescriptorWithLocalVariable(String methodName, String replaceByString) {
      super("'Predicates*." + methodName + "(" + (methodName.equals("equalTo") ? "$v$" : "") + ")", null);
      myReplaceByStringTemplate = replaceByString;
    }

    @Override
    public PsiExpression replace(PsiExpression expression, TypeEvaluator evaluator) {
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
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
      String newExpressionString =
        adjust(((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0], true, myTargetType, evaluator) + ".negate()";

      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiMethodReferenceExpression) {
        expression = replaceTypeCast(expression, parent);
      }
      else if (!isJavaPredicate(parent, evaluator)) {
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

    public AndOrOrConversionDescriptor(PsiType targetType) {
      myTargetType = targetType;
    }

    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
      final String methodName = methodCall.getMethodExpression().getReferenceName();

      final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
      if (arguments.length == 1) {
        return (PsiExpression)expression.replace(elementFactory.createExpressionFromText(adjust(arguments[0], true, myTargetType, evaluator), expression));
      }
      LOG.assertTrue(arguments.length != 0);
      StringBuilder replaceBy = new StringBuilder();
      for (int i = 1; i < arguments.length; i++) {
        PsiExpression argument = arguments[i];
        replaceBy.append(".").append(methodName).append("(").append(adjust(argument, false, myTargetType, evaluator)).append(")");
      }
      replaceBy.insert(0, adjust(arguments[0], true, myTargetType, evaluator));
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiMethodReferenceExpression) {
        expression = replaceTypeCast(expression, parent);
      }
      else if (!isJavaPredicate(parent, evaluator)) {
        replaceBy.append("::test");
      }
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
        if (aClass != null && GuavaPredicateConversionRule.JAVA_PREDICATE.equals(aClass.getQualifiedName())) {
          expression = (PsiExpression)parParent.replace(expression);
        }
      }
    }
    return expression;
  }

  public static boolean isJavaPredicate(PsiElement element, TypeEvaluator evaluator) {
    if (element instanceof PsiLocalVariable) {
      return isJavaPredicate(evaluator.getType(element));
    }
    else if (element instanceof PsiReturnStatement) {
      final PsiElement methodOrLambda = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiLambdaExpression.class);
      PsiType methodReturnType = null;
      if (methodOrLambda instanceof PsiMethod) {
        methodReturnType = evaluator.getType(methodOrLambda);
      }
      return isJavaPredicate(methodReturnType);
    }
    else if (element instanceof PsiExpressionList) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return evaluator.getType(parent) != null;
      }
    }
    return false;
  }

  private static boolean isJavaPredicate(@Nullable PsiType type) {
    PsiClass aClass;
    return (aClass = PsiTypesUtil.getPsiClass(type)) != null && GuavaPredicateConversionRule.JAVA_PREDICATE.equals(aClass.getQualifiedName());
  }

  private static boolean isUnconverted(PsiType type) {
    final PsiClass predicateClass = PsiTypesUtil.getPsiClass(type);
    return predicateClass != null && !GuavaPredicateConversionRule.JAVA_PREDICATE.equals(predicateClass.getQualifiedName());
  }

  private static String adjust(PsiExpression expression, boolean insertTypeCase, PsiType targetType, TypeEvaluator evaluator) {
    if (expression instanceof PsiMethodReferenceExpression) {
      final PsiExpression qualifier = ((PsiMethodReferenceExpression)expression).getQualifierExpression();
      final PsiType evaluatedType = evaluator.evaluateType(qualifier);
      final PsiClass evaluateClass;
      if (evaluatedType != null &&
          (evaluateClass = PsiTypesUtil.getPsiClass(evaluatedType)) != null &&
          GuavaPredicateConversionRule.JAVA_PREDICATE.equals(evaluateClass.getQualifiedName())) {
        return adjust((PsiExpression)expression.replace(qualifier), insertTypeCase, targetType, evaluator);
      }
    }
    if (expression instanceof PsiFunctionalExpression) {
      if (insertTypeCase) {
        return "((" + targetType.getCanonicalText() + ")" + expression.getText() + ")";
      }
    }
    else if (expression instanceof PsiMethodCallExpression || expression instanceof PsiReferenceExpression) {
      if (isUnconverted(evaluator.evaluateType(expression))) {
        expression = (PsiExpression)expression.replace(JavaPsiFacade.getElementFactory(expression.getProject())
                                                         .createExpressionFromText(expression.getText() + "::apply", expression));
        return adjust(expression, insertTypeCase, targetType, evaluator);
      }
    }
    return expression.getText();
  }

}
