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
import com.intellij.psi.*;
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
  private final PsiElementFactory myElementFactory;

  public GuavaFluentIterableMethodConverters(PsiElementFactory elementFactory) {
    myElementFactory = elementFactory;
  }

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

  public PsiLocalVariable convert(final PsiLocalVariable localVariable) {
    final PsiTypeElement typeElement = localVariable.getTypeElement();
    final PsiReferenceParameterList generics = PsiTreeUtil.findChildOfType(typeElement, PsiReferenceParameterList.class);
    typeElement.replace(myElementFactory.createTypeElementFromText(
      StreamApiConstants.JAVA_UTIL_STREAM_STREAM + (generics == null ? "" : generics.getText()), null));

    final PsiExpression initializer = localVariable.getInitializer();
    if (initializer != null) {
      convertMethodCallDeep((PsiMethodCallExpression)initializer);
    }
    return localVariable;
  }

  public PsiElement convert(PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiElement expressionParent = expression.getParent();
      if (expressionParent instanceof PsiReturnStatement || isIterableMethodParameter(expressionParent, expression)) {
        return addCollectionToList(expression);
      }
      return null;
    }
    final PsiMethodCallExpression parentMethodCall = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
    if (parentMethodCall != null && parentMethodCall.getMethodExpression().getQualifierExpression() == expression) {
      final PsiMethod seqTailMethod = parentMethodCall.resolveMethod();
      if (seqTailMethod == null) {
        return null;
      }
      final PsiClass seqTailMethodClass = seqTailMethod.getContainingClass();
      if (seqTailMethodClass != null && GuavaFluentIterableInspection.GUAVA_OPTIONAL.equals(seqTailMethodClass.getQualifiedName())) {
        final PsiMethodCallExpression newParentMethodCall = GuavaOptionalConverter.convertGuavaOptionalToJava(parentMethodCall, myElementFactory);
        expression = newParentMethodCall.getMethodExpression().getQualifierExpression();
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      expression = convertMethodCallDeep((PsiMethodCallExpression)expression);
    }
    if (expression == null) {
      return null;
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
      return addCollectToListIfNeed(expression, parameterType);
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      LOG.assertTrue(containingMethod != null);
      final PsiType returnType = containingMethod.getReturnType();
      return addCollectToListIfNeed(expression, returnType);
    }
    return expression;
  }

  private PsiExpression addCollectToListIfNeed(PsiExpression expression, PsiType type) {
    if (type instanceof PsiClassType && ((PsiClassType)type).rawType().equalsToText(CommonClassNames.JAVA_LANG_ITERABLE)) {
      return (PsiExpression)addCollectionToList(expression);
    }
    return expression;
  }

  private PsiElement addCollectionToList(PsiElement expression) {
    return expression.replace(myElementFactory.createExpressionFromText(expression.getText() + ".collect(java.util.stream.Collectors.toList())", null));
  }

  private static boolean isIterableMethodParameter(PsiElement listExpression, PsiExpression parameterExpression) {
    if (!(listExpression instanceof PsiExpressionList)) {
      return false;
    }
    if (!(listExpression.getParent() instanceof PsiMethodCallExpression)) {
      return false;
    }
    final Project project = parameterExpression.getProject();
    final PsiClass fluentIterable = JavaPsiFacade.getInstance(project).findClass(GuavaFluentIterableInspection.GUAVA_FLUENT_ITERABLE, 
                                                                                 listExpression.getResolveScope());
    return GuavaFluentIterableInspection.isMethodWithParamAcceptsConversion((PsiMethodCallExpression)listExpression.getParent(), parameterExpression, fluentIterable);
  }

  @Nullable
  private PsiMethodCallExpression convertMethodCallDeep(@NotNull PsiMethodCallExpression methodCall) {
    PsiMethodCallExpression newMethodCall = methodCall;
    PsiMethodCallExpression returnCall = null;
    while (true) {
      final PsiReferenceExpression methodExpression = newMethodCall.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      PsiMethodCallExpression converted = convertFromMethodCall(newMethodCall);
      if (converted != null) {
        return returnCall;
      }
      if (TO_OTHER_COLLECTION_METHODS.containsKey(name)) {
        converted = convertToCollection(newMethodCall, name);
      }
      else {
        final FluentIterableMethodTransformer transformer = METHOD_INDEX.get(name);
        LOG.assertTrue(transformer != null, name);
        converted = transformer.transform(newMethodCall, myElementFactory);
      }
      if (converted == null) {
        return returnCall;
      }

      if (returnCall == null) {
        returnCall = converted;
      }
      newMethodCall = converted;
      final PsiExpression expression = newMethodCall.getMethodExpression().getQualifierExpression();
      if (expression instanceof PsiMethodCallExpression) {
        newMethodCall = (PsiMethodCallExpression)expression;
      }
      else {
        return returnCall;
      }
    }
  }
  
  private PsiMethodCallExpression convertFromMethodCall(PsiMethodCallExpression methodCall) {
    if (GuavaFluentIterableInspection.FLUENT_ITERABLE_FROM.equals(methodCall.getMethodExpression().getReferenceName())) {
      final PsiExpression[] argumentList = methodCall.getArgumentList().getExpressions();
      LOG.assertTrue(argumentList.length == 1);
      final PsiExpression expression = argumentList[0];

      final PsiType type = expression.getType();
      LOG.assertTrue(type instanceof PsiClassType);
      final String newExpressionText;
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        newExpressionText = expression.getText() + ".stream()";
      } else {
        newExpressionText = "java.util.stream.StreamSupport.stream(" + expression.getText() + ".spliterator(), false)";
      }
      return (PsiMethodCallExpression)methodCall.replace(myElementFactory.createExpressionFromText(newExpressionText, null));
    }
    return null;
  }

  private PsiMethodCallExpression convertToCollection(final PsiMethodCallExpression methodCall,
                                                      final String methodName) {
    final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
    LOG.assertTrue(expressions.length < 2);
    String template = TO_OTHER_COLLECTION_METHODS.get(methodName);
    if (expressions.length == 1) {
      template = String.format(template, expressions[0].getText());
    }
    final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifier == null) {
      return null;
    }
    final PsiExpression expression = myElementFactory.createExpressionFromText(qualifier.getText() + "." + template, null);
    return (PsiMethodCallExpression)methodCall.replace(expression);
  }
}
