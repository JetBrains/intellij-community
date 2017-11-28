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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.java18StreamApi.StreamApiConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.text.UniqueNameGenerator;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class FluentIterableConversionUtil {
  private final static Logger LOG = Logger.getInstance(FluentIterableConversionUtil.class);

  @Nullable
  static TypeConversionDescriptor getToArrayDescriptor(PsiType initialType, PsiExpression expression) {
    if (!(initialType instanceof PsiClassType)) {
      return null;
    }
    final PsiType[] parameters = ((PsiClassType)initialType).getParameters();
    if (parameters.length != 1) {
      return null;
    }
    final PsiElement methodCall = expression.getParent();
    if (!(methodCall instanceof PsiMethodCallExpression)) {
      return null;
    }

    final PsiExpression[] expressions = ((PsiMethodCallExpression)methodCall).getArgumentList().getExpressions();
    if (expressions.length != 1) {
      return null;
    }
    final PsiExpression classTypeExpression = expressions[0];
    final PsiType targetType = classTypeExpression.getType();
    if (!(targetType instanceof PsiClassType)) {
      return null;
    }
    final PsiType[] targetParameters = ((PsiClassType)targetType).getParameters();
    if (targetParameters.length != 1) {
      return null;
    }
    if (PsiTypesUtil.compareTypes(parameters[0], targetParameters[0], false)) {
      return new TypeConversionDescriptor("$q$.toArray($type$)", null) {
        PsiType myType = parameters[0];

        @Override
        public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
          if (!JavaGenericsUtil.isReifiableType(myType)) {
            final String chosenName = chooseName(expression, PsiType.INT);
            final PsiType arrayType;
            if (myType instanceof PsiClassType) {
              final PsiClass resolvedClass = ((PsiClassType)myType).resolve();
              if (resolvedClass == null) return expression;
              if (resolvedClass instanceof PsiTypeParameter) {
                arrayType = PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
              } else {
                arrayType = JavaPsiFacade.getElementFactory(expression.getProject()).createType(resolvedClass);
              }
            }  else {
              return null;
            }
            setReplaceByString("$q$.toArray(" + chosenName + " -> " + "(" + myType.getCanonicalText(false) + "[]) new " +
                               arrayType.getCanonicalText(false) + "[" + chosenName + "])");
          } else {
            setReplaceByString("$q$.toArray(" + myType.getCanonicalText(false) + "[]::new)");
          }
          return super.replace(expression, evaluator);
        }
      };
    }
    return null;
  }

  public static String chooseName(@NotNull PsiExpression context, @Nullable PsiType type) {
    final UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(context.getProject());
    final String name = codeStyleManager.suggestUniqueVariableName(
      codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type).names[0], context, false);
    return nameGenerator.generateUniqueName(name);
  }

  @Nullable
  static TypeConversionDescriptor getFilterDescriptor(@NotNull PsiMethod method, @Nullable PsiExpression context) {
    LOG.assertTrue("filter".equals(method.getName()));
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return null;
    final PsiParameter parameter = parameters[0];
    final PsiType type = parameter.getType();
    if (!(type instanceof PsiClassType)) return null;
    final PsiClass resolvedClass = ((PsiClassType)type).resolve();
    if (resolvedClass == null) return null;
    if (CommonClassNames.JAVA_LANG_CLASS.equals(resolvedClass.getQualifiedName())) {
      if (context == null) return null;
      PsiMethodCallExpression methodCall = null;
      if (context instanceof PsiMethodCallExpression) {
        methodCall = (PsiMethodCallExpression)context;
      }
      else if (context.getParent() instanceof PsiMethodCallExpression) {
        methodCall = (PsiMethodCallExpression)context.getParent();
      }
      if (methodCall == null) return null;
      final PsiType filteredType = methodCall.getType();
      if (!(filteredType instanceof PsiClassType)) return null;
      final PsiType[] filterParameters = ((PsiClassType)filteredType).getParameters();
      if (filterParameters.length != 1) return null;
      final String filterClassName = getFilterClassText(filterParameters[0]);
      if (filterClassName == null) return null;
      return new GuavaFilterInstanceOfConversionDescriptor(filterClassName);
    }
    else if (GuavaLambda.PREDICATE.getClassQName().equals(resolvedClass.getQualifiedName())) {
      return new GuavaTypeConversionDescriptor("$it$.filter($p$)", "$it$." + StreamApiConstants.FILTER + "($p$)", context);
    }
    return null;
  }

  @Nullable
  private static String getFilterClassText(PsiType type) {
    final PsiClass filterClass = PsiUtil.resolveClassInType(type);
    if (filterClass != null) return filterClass.getQualifiedName();
    if (type instanceof PsiCapturedWildcardType) {
      final PsiClass boundClass = PsiUtil.resolveClassInType(((PsiCapturedWildcardType)type).getUpperBound());
      if (boundClass != null) return boundClass.getQualifiedName();
    }
    return null;
  }

  static class TransformAndConcatConversionRule extends GuavaTypeConversionDescriptor {
    public TransformAndConcatConversionRule(PsiExpression context) {
      super("$q$.transformAndConcat($params$)", "$q$.flatMap($params$)", context);
    }

    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator typeEvaluator) {
      PsiExpression argument = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0];

      PsiAnonymousClass anonymousClass;
      if (argument instanceof PsiNewExpression &&
          (anonymousClass = ((PsiNewExpression)argument).getAnonymousClass()) != null) {
        argument = GuavaConversionUtil.convertAnonymousClass((PsiNewExpression)argument, anonymousClass, typeEvaluator);
      }
      final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(expression.getProject());
      if (argument != null && !(argument instanceof PsiFunctionalExpression)) {
        argument =
          (PsiExpression)argument.replace(javaPsiFacade.getElementFactory().createExpressionFromText("(" + argument.getText() + ")::apply", null));
        ParenthesesUtils.removeParentheses(argument, false);
      }

      if (argument instanceof PsiMethodReferenceExpression) {
        argument = LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)argument, true, true);
      }
      if (argument instanceof PsiLambdaExpression) {
        List<Pair<PsiExpression, Boolean>> iterableReturnValues = new SmartList<>();

        final PsiElement body = ((PsiLambdaExpression)argument).getBody();
        final PsiClass collection = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_COLLECTION, expression.getResolveScope());
        if (collection == null) return expression;
        final PsiClass iterable = javaPsiFacade.findClass(CommonClassNames.JAVA_LANG_ITERABLE, expression.getResolveScope());
        if (iterable == null) return expression;

        if (body instanceof PsiCodeBlock) {
          for (PsiReturnStatement statement : PsiUtil.findReturnStatements((PsiCodeBlock)body)) {
            final PsiExpression retValue = statement.getReturnValue();
            if (!determineType(retValue, iterableReturnValues, iterable, collection)) {
              return expression;
            }
          }
        } else if (!(body instanceof PsiExpression) || !determineType((PsiExpression)body, iterableReturnValues, iterable, collection)) {
          return expression;
        }

        for (Pair<PsiExpression, Boolean> returnValueAndIsCollection : iterableReturnValues) {
          convertToStream(returnValueAndIsCollection.getFirst(), returnValueAndIsCollection.getSecond());
        }

      } else {
        return expression;
      }

      return super.replace(expression, typeEvaluator);
    }

    private static boolean determineType(PsiExpression retValue,
                                         List<Pair<PsiExpression, Boolean>> iterableReturnValues,
                                         PsiClass iterable,
                                         PsiClass collection) {
      if (retValue == null) return false;
      PsiType type = retValue.getType();
      if (PsiType.NULL.equals(type)) {
        return true;
      }
      if (type instanceof PsiCapturedWildcardType) {
        type = ((PsiCapturedWildcardType)type).getUpperBound();
      }
      if (type instanceof PsiClassType) {
        final PsiClass resolvedClass = ((PsiClassType)type).resolve();

        if (InheritanceUtil.isInheritorOrSelf(resolvedClass, iterable, true)) {
          final boolean isCollection = InheritanceUtil.isInheritorOrSelf(resolvedClass, collection, true);
          iterableReturnValues.add(Pair.create(retValue, isCollection));
          return true;
        }
      }
      return false;
    }

    private static void convertToStream(@NotNull PsiExpression returnValue, boolean isCollection) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(returnValue.getProject());
      PsiExpression newExpression;
      if (isCollection) {
        String expressionAsText = "(" + returnValue.getText() + ").stream()";
        newExpression = elementFactory.createExpressionFromText(expressionAsText, returnValue);
        ParenthesesUtils.removeParentheses(newExpression, false);
      }
      else {
        final String methodCall = "(" + returnValue.getText() + ")";
        final boolean needParentheses = ParenthesesUtils
          .areParenthesesNeeded((PsiParenthesizedExpression)elementFactory.createExpressionFromText(methodCall, null), false);
        String expressionAsText = "java.util.stream.StreamSupport.stream(" + (needParentheses ? methodCall : methodCall.substring(1, methodCall.length() - 1)) + ".spliterator(), false)";
        newExpression = elementFactory.createExpressionFromText(expressionAsText, returnValue);
      }
      returnValue.replace(newExpression);
    }
  }

  private static class GuavaFilterInstanceOfConversionDescriptor extends TypeConversionDescriptor {
    public GuavaFilterInstanceOfConversionDescriptor(String filterClassQName) {
      super("$it$.filter($p$)", "$it$." + StreamApiConstants.FILTER + "(" + filterClassQName + ".class::isInstance)." + StreamApiConstants.MAP + "(" + filterClassQName + ".class::cast)");
    }

    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
      return super.replace(expression, evaluator);
    }
  }

  static TypeConversionDescriptor createToCollectionDescriptor(@Nullable String methodName,
                                                               @NotNull PsiExpression context) {
    final String findTemplate;
    final String replaceTemplate;
    final String returnType;
    if ("toMap".equals(methodName) || "uniqueIndex".equals(methodName)) {
      final GuavaTypeConversionDescriptor descriptor = new GuavaTypeConversionDescriptor("$it$.$methodName$($f$)",
                                                                                         "$it$.collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity(), $f$))", context);
      return descriptor.withConversionType(GuavaConversionUtil.addTypeParameters(CommonClassNames.JAVA_UTIL_MAP, context.getType(), context));
    }
    else if ("toList".equals(methodName)) {
      findTemplate = "$it$.toList()";
      replaceTemplate = GuavaFluentIterableConversionRule.STREAM_COLLECT_TO_LIST;
      returnType = CommonClassNames.JAVA_UTIL_LIST;
    }
    else if ("toSet".equals(methodName)) {
      findTemplate = "$it$.toSet()";
      replaceTemplate = "$it$.collect(java.util.stream.Collectors.toSet())";
      returnType = CommonClassNames.JAVA_UTIL_SET;
    }
    else if ("toSortedList".equals(methodName)) {
      findTemplate = "$it$.toSortedList($c$)";
      replaceTemplate = "$it$.sorted($c$).collect(java.util.stream.Collectors.toList())";
      returnType = CommonClassNames.JAVA_UTIL_LIST;
    }
    else if ("toSortedSet".equals(methodName)) {
      findTemplate = "$it$.toSortedSet($c$)";
      replaceTemplate = "$it$.collect(java.util.stream.Collectors.toCollection(() -> new java.util.TreeSet<>($c$)))";
      returnType = CommonClassNames.JAVA_UTIL_SET;
    } else {
      return null;
    }
    final PsiType type = GuavaConversionUtil.addTypeParameters(returnType, context.getType(), context);
    return new TypeConversionDescriptor(findTemplate, replaceTemplate).withConversionType(type);
  }

  static class CopyIntoConversionDescriptor extends TypeConversionDescriptor {
    public CopyIntoConversionDescriptor() {
      super("$it$.copyInto($c$)", null);
    }

    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(expression.getProject());
      final PsiClass javaUtilCollection = facade.findClass(CommonClassNames.JAVA_UTIL_COLLECTION, expression.getResolveScope());
      LOG.assertTrue(javaUtilCollection != null);
      final PsiClassType assignableCollection = facade.getElementFactory().createType(javaUtilCollection, getQualifierElementType((PsiMethodCallExpression)expression));
      final PsiType actualType = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0].getType();
      final String replaceTemplate;
      if (actualType == null || TypeConversionUtil.isAssignable(assignableCollection, actualType)) {
        replaceTemplate = "$it$.collect(java.util.stream.Collectors.toCollection(() -> $c$))";
      } else  {
        String varName = chooseName(expression, assignableCollection);
        replaceTemplate = "$it$.collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), " + varName + " -> {\n" +
                          "            $c$.addAll(" + varName + ");\n" +
                          "            return $c$;\n" +
                          "        }))";
      }
      setReplaceByString(replaceTemplate);
      return super.replace(expression, evaluator);
    }

    @Nullable
    private static PsiType getQualifierElementType(PsiMethodCallExpression expression) {
      final PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return null;
      final PsiType type = qualifier.getType();
      if (!(type instanceof PsiClassType)) return null;
      PsiType[] parameters = ((PsiClassType)type).getParameters();
      if (parameters.length > 1) return null;
      if (parameters.length == 0) return PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
      return parameters[0];
    }
  }

}
