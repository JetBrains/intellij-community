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
package com.intellij.codeInspection.java18StreamApi;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class GuavaFluentIterableMethodConverters {
  private static final Logger LOG = Logger.getInstance(GuavaFluentIterableMethodConverters.class);

  private static final Map<String, FluentIterableMethodTransformer> METHOD_INDEX = new HashMap<String, FluentIterableMethodTransformer>();
  private static final Map<String, String> TO_OTHER_COLLECTION_METHODS = new HashMap<String, String>();
  private static final Set<String> STOP_METHODS = new HashSet<String>();

  static {
    METHOD_INDEX.put("allMatch", new FluentIterableMethodTransformer.OneParameterMethodTransformer(StreamApiConstants.ALL_MATCH + "(%s)", true));
    METHOD_INDEX.put("anyMatch", new FluentIterableMethodTransformer.OneParameterMethodTransformer(StreamApiConstants.ANY_MATCH + "(%s)", true));

    METHOD_INDEX.put("contains", new FluentIterableMethodTransformer.OneParameterMethodTransformer("anyMatch(e -> e != null && e.equals(%s))"));
    METHOD_INDEX.put("copyInto", new FluentIterableMethodTransformer.OneParameterMethodTransformer("forEach(%s::add)"));
    METHOD_INDEX.put("filter", new FluentIterableMethodTransformer.OneParameterMethodTransformer(StreamApiConstants.FILTER + "(%s)", true));
    METHOD_INDEX.put("first", new FluentIterableMethodTransformer.ParameterlessMethodTransformer(StreamApiConstants.FIND_FIRST));
    METHOD_INDEX.put("firstMatch", new FluentIterableMethodTransformer.OneParameterMethodTransformer("filter(%s).findFirst()", true));
    METHOD_INDEX.put("get", new FluentIterableMethodTransformer.OneParameterMethodTransformer("collect(java.util.stream.Collectors.toList()).get(%s)"));
    METHOD_INDEX
      .put("index", new FluentIterableMethodTransformer.OneParameterMethodTransformer("collect(java.util.stream.Collectors.toList()).indexOf(%s)"));
    METHOD_INDEX.put("isEmpty", new FluentIterableMethodTransformer.ParameterlessMethodTransformer("findAny().isPresent()", true));
    METHOD_INDEX.put("last", new FluentIterableMethodTransformer.ParameterlessMethodTransformer("reduce((previous, current) -> current)"));
    METHOD_INDEX.put("limit", new FluentIterableMethodTransformer.OneParameterMethodTransformer(StreamApiConstants.LIMIT + "(%s)"));
    METHOD_INDEX.put("size", new FluentIterableMethodTransformer.ParameterlessMethodTransformer("collect(java.util.stream.Collectors.toList()).size()"));
    METHOD_INDEX.put("skip", new FluentIterableMethodTransformer.OneParameterMethodTransformer(StreamApiConstants.SKIP + "(%s)"));
    METHOD_INDEX.put("toArray", new FluentIterableMethodTransformer.ToArrayMethodTransformer());
    METHOD_INDEX.put("transform", new FluentIterableMethodTransformer.OneParameterMethodTransformer(StreamApiConstants.MAP + "(%s)", true));
    METHOD_INDEX.put("transformAndConcat", new FluentIterableMethodTransformer.OneParameterMethodTransformer(StreamApiConstants.FLAT_MAP + "(%s)", true));
    METHOD_INDEX.put("uniqueIndex", new FluentIterableMethodTransformer.OneParameterMethodTransformer(
      "collect(java.util.stream.Collectors.toMap(%s, java.util.function.Function.identity()))"));

    TO_OTHER_COLLECTION_METHODS.put("toMap", "collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity(), %s))");
    TO_OTHER_COLLECTION_METHODS.put("toList", "collect(java.util.stream.Collectors.toList())");
    TO_OTHER_COLLECTION_METHODS.put("toSet", "collect(java.util.stream.Collectors.toSet())");
    TO_OTHER_COLLECTION_METHODS.put("toSortedList", "sorted(%s).collect(java.util.stream.Collectors.toList())");
    TO_OTHER_COLLECTION_METHODS.put("toSortedSet", "sorted(%s).collect(java.util.stream.Collectors.toSet())");

    STOP_METHODS.add("append");
    STOP_METHODS.add("cycle");
  }

  public static boolean isFluentIterableMethod(final String methodName) {
    return STOP_METHODS.contains(methodName) ||
           TO_OTHER_COLLECTION_METHODS.containsKey(methodName) ||
           METHOD_INDEX.containsKey(methodName);
  }

  public static boolean isStopMethod(final String methodName) {
    return STOP_METHODS.contains(methodName);
  }

  public static void convert(final PsiLocalVariable localVariable,
                             final PsiElementFactory elementFactory,
                             final JavaCodeStyleManager codeStyleManager) {
    final PsiTypeElement typeElement = localVariable.getTypeElement();
    final PsiReferenceParameterList generics = PsiTreeUtil.findChildOfType(typeElement, PsiReferenceParameterList.class);
    typeElement.replace(elementFactory.createTypeElementFromText(
      StreamApiConstants.JAVA_UTIL_STREAM_STREAM + (generics == null ? "" : generics.getText()), null));

    final PsiExpression initializer = localVariable.getInitializer();
    if (initializer != null) {
      PsiMethodCallExpression initializerMethodCall = (PsiMethodCallExpression)initializer;
      convertMethodCallDeep(elementFactory, initializerMethodCall);
    }
    codeStyleManager.shortenClassReferences(localVariable);
  }

  public static void convert(PsiExpression expression,
                             final PsiElementFactory elementFactory,
                             final JavaCodeStyleManager codeStyleManager) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiElement expressionParent = expression.getParent();
      if (expressionParent instanceof PsiReturnStatement || isIterableMethodParameter(expressionParent, expression)) {
        expression = (PsiExpression)expression.replace(
          elementFactory.createExpressionFromText(expression.getText() + ".collect(java.util.stream.Collectors.toList())", null));
        codeStyleManager.shortenClassReferences(expression);
      }
      return;
    }
    final PsiMethodCallExpression parentMethodCall = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
    if (parentMethodCall != null && parentMethodCall.getMethodExpression().getQualifierExpression() == expression) {
      final PsiMethod seqTailMethod = parentMethodCall.resolveMethod();
      if (seqTailMethod == null) {
        return;
      }
      final PsiClass seqTailMethodClass = seqTailMethod.getContainingClass();
      if (seqTailMethodClass != null && GuavaFluentIterableInspection.GUAVA_OPTIONAL.equals(seqTailMethodClass.getQualifiedName())) {
        final PsiMethodCallExpression newParentMethodCall =
          GuavaOptionalConverter.convertGuavaOptionalToJava(parentMethodCall, elementFactory);
        expression = newParentMethodCall.getMethodExpression().getQualifierExpression();
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      expression = convertMethodCallDeep(elementFactory, (PsiMethodCallExpression)expression);
    }
    if (expression == null) {
      return;
    }
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpressionList) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent.getParent();
      PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
      int index = ArrayUtil.indexOf(expressions, expression);
      LOG.assertTrue(index >= 0);
      final PsiMethod method = methodCall.resolveMethod();
      LOG.assertTrue(method != null);
      final PsiType parameterType = method.getParameterList().getParameters()[index].getType();
      expression = addCollectToListIfNeed(expression, parameterType, elementFactory);
    } else if (parent instanceof PsiReturnStatement) {
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      LOG.assertTrue(containingMethod != null);
      final PsiType returnType = containingMethod.getReturnType();
      expression = addCollectToListIfNeed(expression, returnType, elementFactory);
    }
    codeStyleManager.shortenClassReferences(expression);
  }

  private static PsiExpression addCollectToListIfNeed(PsiExpression expression, PsiType type, PsiElementFactory elementFactory) {
    if (type instanceof PsiClassType) {
      PsiClass resolvedParamClass = ((PsiClassType)type).resolve();
      if (resolvedParamClass != null && CommonClassNames.JAVA_LANG_ITERABLE.equals(resolvedParamClass.getQualifiedName())) {
        final PsiExpression newExpression =
          elementFactory.createExpressionFromText(expression.getText() + ".collect(java.util.stream.Collectors.toList())", null);
        return (PsiExpression) expression.replace(newExpression);
      }
    }
    return expression;
  }

  private static boolean isIterableMethodParameter(PsiElement listExpression, PsiExpression parameterExpression) {
    if (!(listExpression instanceof PsiExpressionList)) {
      return false;
    }
    if (!(listExpression.getParent() instanceof PsiMethodCallExpression)) {
      return false;
    }
    final Project project = parameterExpression.getProject();
    final PsiClass fluentIterable = JavaPsiFacade.getInstance(project)
      .findClass(GuavaFluentIterableInspection.GUAVA_FLUENT_ITERABLE, GlobalSearchScope.allScope(project));
    return GuavaFluentIterableInspection.isMethodWithParamAcceptsConversion((PsiMethodCallExpression)listExpression.getParent(), parameterExpression, fluentIterable);
  }

  @Nullable
  private static PsiMethodCallExpression convertMethodCallDeep(PsiElementFactory elementFactory,
                                                               @NotNull PsiMethodCallExpression methodCall) {
    PsiMethodCallExpression newMethodCall = methodCall;
    PsiMethodCallExpression returnCall = null;
    while (true) {
      final Pair<PsiMethodCallExpression, Boolean> converted = convertMethodCall(elementFactory, newMethodCall);
      if (converted.getSecond()) {
        return returnCall;
      }
      if (returnCall == null) {
        returnCall = converted.getFirst();
      }
      if (converted.getFirst() == null) {
        return returnCall;
      }
      newMethodCall = converted.getFirst();
      final PsiExpression expression = newMethodCall.getMethodExpression().getQualifierExpression();
      if (expression instanceof PsiMethodCallExpression) {
        newMethodCall = (PsiMethodCallExpression)expression;
      }
      else {
        return returnCall;
      }
    }
  }

  public static Pair<PsiMethodCallExpression, Boolean> convertMethodCall(PsiElementFactory elementFactory, PsiMethodCallExpression methodCall) {
    final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (TO_OTHER_COLLECTION_METHODS.containsKey(name)) {
      return Pair.create(convertToCollection(methodCall, name, elementFactory), false);
    }
    else if (GuavaFluentIterableInspection.FLUENT_ITERABLE_FROM.equals(name)) {
      final PsiExpression[] argumentList = methodCall.getArgumentList().getExpressions();
      LOG.assertTrue(argumentList.length == 1);
      final PsiExpression expression = argumentList[0];

      final PsiType type = expression.getType();
      LOG.assertTrue(type instanceof PsiClassType);
      final PsiClass resolvedClass = ((PsiClassType)type).resolve();
      final String newExpressionText;
      if (InheritanceUtil.isInheritor(resolvedClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        newExpressionText = expression.getText() + ".stream()";
      } else {
        newExpressionText = "java.util.stream.StreamSupport.stream(" + expression.getText() + ".spliterator(), false)";
      }
      return Pair.create((PsiMethodCallExpression)methodCall.replace(elementFactory.createExpressionFromText(newExpressionText, null)), true);
    }
    else {
      final FluentIterableMethodTransformer transformer = METHOD_INDEX.get(name);
      LOG.assertTrue(transformer != null, name);
      final PsiMethodCallExpression transformedExpression = transformer.transform(methodCall, elementFactory);
      return Pair.create(transformedExpression, false);
    }
  }

  private static PsiMethodCallExpression convertToCollection(final PsiMethodCallExpression methodCall,
                                                             final String methodName,
                                                             final PsiElementFactory elementFactory) {
    final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
    assert expressions.length < 2;
    String template = TO_OTHER_COLLECTION_METHODS.get(methodName);
    if (expressions.length == 1) {
      template = String.format(template, expressions[0].getText());
    }
    final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifier == null) {
      return null;
    }
    final String text = qualifier.getText() + "." + template;
    final PsiExpression expression = elementFactory.createExpressionFromText(text, null);
    return (PsiMethodCallExpression)methodCall.replace(expression);
  }
}
