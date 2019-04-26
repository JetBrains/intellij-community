/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class FunctionalInterfaceSuggester {
  public static final String[] FUNCTIONAL_INTERFACES = {
    //old jdk without annotations
    CommonClassNames.JAVA_LANG_RUNNABLE,
    CommonClassNames.JAVA_UTIL_CONCURRENT_CALLABLE,
    CommonClassNames.JAVA_UTIL_COMPARATOR,

    //IDEA
    "com.intellij.util.Function",
    "com.intellij.util.Consumer",
    "com.intellij.openapi.util.Computable",
    "com.intellij.openapi.util.Condition",
    "com.intellij.util.Processor",
    "com.intellij.util.Producer",

    //guava
    "com.google.common.base.Function",
    "com.google.common.base.Predicate",
    "com.google.common.base.Supplier",

    //common collections
    "org.apache.commons.collections.Closure",
    "org.apache.commons.collections.Factory",
    "org.apache.commons.collections.Predicate",
    "org.apache.commons.collections.Transformer",

    //trove
    "gnu.trove.TObjectFunction",
    "gnu.trove.TObjectProcedure",
    "gnu.trove.TObjectObjectProcedure",
    };

  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiFunctionalExpression expression) {
    final PsiType qualifierType = expression instanceof PsiMethodReferenceExpression 
                                  ? PsiMethodReferenceUtil.getQualifierType((PsiMethodReferenceExpression)expression) : null;
    return suggestFunctionalInterfaces(expression, aClass -> composeAcceptableType(aClass, expression, qualifierType));
  }

  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiMethod method) {
    return suggestFunctionalInterfaces(method, false);
  }

  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiMethod method,
                                                                          boolean suggestUnhandledThrowables) {
    if (method.isConstructor()) {
      return Collections.emptyList();
    }

    return suggestFunctionalInterfaces(method, aClass -> {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass);
      if (interfaceMethod != null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final PsiParameter[] interfaceMethodParameters = interfaceMethod.getParameterList().getParameters();
        if (parameters.length != interfaceMethodParameters.length) {
          return Collections.emptyList();
        }

        final PsiType[] left = new PsiType[parameters.length + 1];
        final PsiType[] right = new PsiType[parameters.length + 1];

        for (int i = 0; i < parameters.length; i++) {
          left[i]  = interfaceMethodParameters[i].getType();
          right[i] = parameters[i].getType();
        }

        left[parameters.length] = method.getReturnType();
        right[parameters.length] = interfaceMethod.getReturnType();

        final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
        final PsiSubstitutor substitutor = PsiResolveHelper.SERVICE.getInstance(aClass.getProject())
          .inferTypeArguments(typeParameters, left, right, PsiUtil.getLanguageLevel(method));
        if (PsiUtil.isRawSubstitutor(aClass, substitutor)) {
          return Collections.emptyList();
        }

        for (int i = 0; i < interfaceMethodParameters.length; i++) {
          PsiType paramType = parameters[i].getType();
          PsiType interfaceParamType = substitutor.substitute(interfaceMethodParameters[i].getType());
          if (!(interfaceParamType instanceof PsiPrimitiveType
                ? paramType.equals(interfaceParamType) : TypeConversionUtil.isAssignable(paramType, interfaceParamType))) {
            return Collections.emptyList();
          }
        }

        final PsiType returnType = method.getReturnType();
        PsiType interfaceMethodReturnType = substitutor.substitute(interfaceMethod.getReturnType());
        if (returnType != null && !TypeConversionUtil.isAssignable(returnType, interfaceMethodReturnType)) {
          return Collections.emptyList();
        }
        
        if (interfaceMethodReturnType instanceof PsiPrimitiveType && !interfaceMethodReturnType.equals(returnType)) {
          return Collections.emptyList();
        }

        final PsiClassType[] interfaceThrownTypes = interfaceMethod.getThrowsList().getReferencedTypes();
        final PsiClassType[] thrownTypes = method.getThrowsList().getReferencedTypes();
        for (PsiClassType thrownType : thrownTypes) {
          if (!ExceptionUtil.isHandledBy(thrownType, interfaceThrownTypes, substitutor)) {
            return Collections.emptyList();
          }
        }

        if (!suggestUnhandledThrowables) {
          for (PsiClassType thrownType : interfaceThrownTypes) {
            final PsiCodeBlock codeBlock = PsiTreeUtil.getContextOfType(method, PsiCodeBlock.class);
            PsiType substitutedThrowable = substitutor.substitute(thrownType);
            if (codeBlock == null || !(substitutedThrowable instanceof PsiClassType) ||
                !ExceptionUtil.isHandled((PsiClassType)substitutedThrowable, codeBlock)) {
              return Collections.emptyList();
            }
          }
        }

        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
        return Collections.singletonList(elementFactory.createType(aClass, substitutor));
      }
      return Collections.emptyList();
    });
  }

  private static <T extends PsiElement> Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull T element, final Function<? super PsiClass, ? extends List<? extends PsiType>> acceptanceChecker) {
    final Project project = element.getProject();
    final Set<PsiType> types = new HashSet<>();
    final Processor<PsiMember> consumer = member -> {
      if (member instanceof PsiClass && Java15APIUsageInspection.getLastIncompatibleLanguageLevel(member, PsiUtil.getLanguageLevel(element)) == null) {
        if (!JavaPsiFacade.getInstance(project).getResolveHelper().isAccessible(member, element, null)) {
          return true;
        }
        types.addAll(acceptanceChecker.apply((PsiClass)member));
      }
      return true;
    };
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    final PsiClass functionalInterfaceClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, allScope);
    if (functionalInterfaceClass != null) {
      AnnotatedMembersSearch.search(functionalInterfaceClass, element.getResolveScope()).forEach(consumer);
    }

    for (String functionalInterface : FUNCTIONAL_INTERFACES) {
      final PsiClass aClass = psiFacade.findClass(functionalInterface, element.getResolveScope());
      if (aClass != null) {
        consumer.process(aClass);
      }
    }

    final ArrayList<PsiType> typesToSuggest = new ArrayList<>(types);
    Collections.sort(typesToSuggest, Comparator.comparing(PsiType::getCanonicalText));
    return typesToSuggest;
  }

  private static List<? extends PsiType> composeAcceptableType(@NotNull PsiClass interface2Consider,
                                                             @NotNull PsiFunctionalExpression expression,
                                                             PsiType qualifierType) {
    final PsiType type = JavaPsiFacade.getElementFactory(interface2Consider.getProject()).createType(interface2Consider, PsiSubstitutor.EMPTY);
    if (expression.isAcceptable(type)) {
      return Collections.singletonList(type);
    }

    return composeAcceptableType1(interface2Consider, expression, qualifierType);
  }

  @NotNull
  private static List<? extends PsiType> composeAcceptableType1(final PsiClass interface2Consider,
                                                               final PsiFunctionalExpression expression,
                                                               final PsiType qualifierType) {
    if (interface2Consider.hasTypeParameters()) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interface2Consider);
      if (interfaceMethod != null) {
        final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
        Project project = interface2Consider.getProject();
        PsiType returnType = interfaceMethod.getReturnType();
        if (expression instanceof PsiLambdaExpression && ((PsiLambdaExpression)expression).hasFormalParameterTypes()) {
          PsiParameter[] functionalExprParameters = ((PsiLambdaExpression)expression).getParameterList().getParameters();
          if (parameters.length != functionalExprParameters.length) {
            return Collections.emptyList();
          }

          final PsiType[] left = new PsiType[parameters.length + 1];
          final PsiType[] right = new PsiType[parameters.length + 1];
          for (int i = 0; i < functionalExprParameters.length; i++) {
            left[i] = parameters[i].getType();
            right[i] = functionalExprParameters[i].getType();
          }

          List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(((PsiLambdaExpression)expression));
          left[parameters.length] = returnExpressions.isEmpty() ? PsiType.VOID : returnExpressions.get(0).getType();
          right[parameters.length] = returnType;

          final PsiSubstitutor substitutor = PsiResolveHelper.SERVICE.getInstance(project)
            .inferTypeArguments(interface2Consider.getTypeParameters(), left, right, PsiUtil.getLanguageLevel(expression));

          PsiType type = JavaPsiFacade.getElementFactory(project).createType(interface2Consider, substitutor);

          if (expression.isAcceptable(type)) {
            return Collections.singletonList(type);
          }
        }
        else if (expression instanceof PsiMethodReferenceExpression) {
          List<PsiType> types = new ArrayList<>();
          for (JavaResolveResult result : ((PsiMethodReferenceExpression)expression).multiResolve(true)) {
            final PsiElement element = result.getElement();
            if (element instanceof PsiMethod) {
              PsiMethod method = (PsiMethod)element;
              int offset = hasOffset((PsiMethodReferenceExpression)expression, method) ? 1 : 0;
              final PsiParameter[] targetMethodParameters = method.getParameterList().getParameters();
              if (targetMethodParameters.length + offset == parameters.length) {
                final PsiType[] left = new PsiType[parameters.length + 1];
                final PsiType[] right = new PsiType[parameters.length + 1];
                if (offset > 0) {
                  if (qualifierType == null) {
                    continue;
                  }
                  left[0] = parameters[0].getType();
                  right[0] = qualifierType;
                }

                for (int i = 0; i < targetMethodParameters.length; i++) {
                  left[i + offset] = parameters[i + offset].getType();
                  right[i + offset] = targetMethodParameters[i].getType();
                }

                left[parameters.length] = method.isConstructor() ? qualifierType : method.getReturnType();
                right[parameters.length] = returnType;

                final PsiSubstitutor substitutor = PsiResolveHelper.SERVICE.getInstance(project)
                  .inferTypeArguments(interface2Consider.getTypeParameters(), left, right, PsiUtil.getLanguageLevel(expression));

                PsiType type = JavaPsiFacade.getElementFactory(project).createType(interface2Consider, substitutor);

                if (expression.isAcceptable(type)) {
                  types.add(type);
                }
              }
            }
          }
          return types;
        }
      }
    }
    return Collections.emptyList();
  }

  private static boolean hasOffset(PsiMethodReferenceExpression expression, PsiMethod method) {
    return PsiMethodReferenceUtil.isStaticallyReferenced(expression) &&
           !method.hasModifierProperty(PsiModifier.STATIC) &&
           !method.isConstructor();
  }
}
