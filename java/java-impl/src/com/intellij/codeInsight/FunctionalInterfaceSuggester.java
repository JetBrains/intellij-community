/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInspection.java15api.Java15APIUsageInspectionBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    if (method.isConstructor()) {
      return Collections.emptyList();
    }

    return suggestFunctionalInterfaces(method, aClass -> {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass);
      if (interfaceMethod != null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final PsiParameter[] interfaceMethodParameters = interfaceMethod.getParameterList().getParameters();
        if (parameters.length != interfaceMethodParameters.length) {
          return null;
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
          return null;
        }

        for (int i = 0; i < interfaceMethodParameters.length; i++) {
          if (!TypeConversionUtil.isAssignable(parameters[i].getType(), substitutor.substitute(interfaceMethodParameters[i].getType()))) {
            return null;
          }
        }

        final PsiType returnType = method.getReturnType();
        PsiType interfaceMethodReturnType = interfaceMethod.getReturnType();
        if (returnType != null && !TypeConversionUtil.isAssignable(returnType, substitutor.substitute(interfaceMethodReturnType))) {
          return null;
        }

        if (PsiType.VOID.equals(returnType) && !PsiType.VOID.equals(interfaceMethodReturnType)) {
          return null;
        }

        final PsiClassType[] interfaceThrownTypes = interfaceMethod.getThrowsList().getReferencedTypes();
        final PsiClassType[] thrownTypes = method.getThrowsList().getReferencedTypes();
        for (PsiClassType thrownType : thrownTypes) {
          if (!ExceptionUtil.isHandledBy(thrownType, interfaceThrownTypes, substitutor)) {
            return null;
          }
        }

        for (PsiClassType thrownType : interfaceThrownTypes) {
          final PsiCodeBlock codeBlock = PsiTreeUtil.getContextOfType(method, PsiCodeBlock.class);
          if (codeBlock == null || !ExceptionUtil.isHandled(thrownType, codeBlock)) {
            return null;
          }
        }

        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
        return elementFactory.createType(aClass, substitutor);
      }
      return null;
    });
  }

  private static <T extends PsiElement> Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull T element, final NullableFunction<PsiClass, PsiType> acceptanceChecker) {
    final Project project = element.getProject();
    final Set<PsiType> types = new HashSet<>();
    final Processor<PsiMember> consumer = member -> {
      if (member instanceof PsiClass && !Java15APIUsageInspectionBase.isForbiddenApiUsage(member, PsiUtil.getLanguageLevel(element))) {
        if (!JavaResolveUtil.isAccessible(member, null, member.getModifierList(), element, null, null)) {
          return true;
        }
        ContainerUtil.addIfNotNull(types, acceptanceChecker.fun((PsiClass)member));
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
      final PsiClass aClass = psiFacade.findClass(functionalInterface, allScope);
      if (aClass != null) {
        consumer.process(aClass);
      }
    }

    final ArrayList<PsiType> typesToSuggest = new ArrayList<>(types);
    Collections.sort(typesToSuggest, (o1, o2) -> o1.getCanonicalText().compareTo(o2.getCanonicalText()));
    return typesToSuggest;
  }

  private static PsiType composeAcceptableType(@NotNull PsiClass interface2Consider,
                                               @NotNull PsiFunctionalExpression expression,
                                               PsiType qualifierType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(interface2Consider.getProject());
    final PsiType type = elementFactory.createType(interface2Consider, PsiSubstitutor.EMPTY);
    if (expression.isAcceptable(type)) {
      return type;
    }

    return composeAcceptableType(interface2Consider, expression, qualifierType, elementFactory);
  }

  private static PsiType composeAcceptableType(final PsiClass interface2Consider,
                                               final PsiFunctionalExpression expression,
                                               PsiType qualifierType, final PsiElementFactory elementFactory) {

    if (interface2Consider.hasTypeParameters()) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interface2Consider);
      if (interfaceMethod != null) {
        final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
        PsiParameter[] functionalExprParameters;
        int offset = 0;
        final PsiType[] left;
        final PsiType[] right;
        if (expression instanceof PsiLambdaExpression && ((PsiLambdaExpression)expression).hasFormalParameterTypes()) {
          left = new PsiType[parameters.length];
          right = new PsiType[parameters.length];
          functionalExprParameters = ((PsiLambdaExpression)expression).getParameterList().getParameters();
          if (parameters.length != functionalExprParameters.length) {
            return null;
          }
        }
        else if (expression instanceof PsiMethodReferenceExpression) {
          left = new PsiType[parameters.length + 1];
          right = new PsiType[parameters.length + 1];
          final PsiMethod method = getTargetMethod((PsiMethodReferenceExpression)expression, qualifierType, parameters, left, right);
          if (method == null) {
            return null;
          }
          functionalExprParameters = method.getParameterList().getParameters();
          if (PsiMethodReferenceUtil.isStaticallyReferenced((PsiMethodReferenceExpression)expression) && !method.hasModifierProperty(PsiModifier.STATIC)) {
            offset = 1;
          }

          left[parameters.length] = method.getReturnType();
          right[parameters.length] = interfaceMethod.getReturnType();
        } else {
          return null;
        }

        for (int i = 0; i < functionalExprParameters.length; i++) {
          left [i + offset] = parameters[i + offset].getType();
          right[i + offset] = functionalExprParameters[i].getType();
        }

        final PsiSubstitutor substitutor = PsiResolveHelper.SERVICE.getInstance(interface2Consider.getProject())
          .inferTypeArguments(interface2Consider.getTypeParameters(), left, right, PsiUtil.getLanguageLevel(expression));

        PsiType type = elementFactory.createType(interface2Consider, substitutor);

        if (expression.isAcceptable(type)) {
          return type;
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiMethod getTargetMethod(PsiMethodReferenceExpression expression,
                                           PsiType qualifierType, 
                                           PsiParameter[] parameters,
                                           PsiType[] left,
                                           PsiType[] right) {
    final boolean staticallyReferenced = PsiMethodReferenceUtil.isStaticallyReferenced(expression);
    final JavaResolveResult[] results = expression.multiResolve(true);
    for (JavaResolveResult result : results) {
      final PsiElement element = result.getElement();
      if (element instanceof PsiMethod) {
        int offset = staticallyReferenced && !((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC) ? 1 : 0;
        final PsiParameter[] functionalExprParameters = ((PsiMethod)element).getParameterList().getParameters();
        if (functionalExprParameters.length + offset == parameters.length) {
          if (offset > 0) {
            if (qualifierType == null) {
              continue;
            }

            left[0]  = parameters[0].getType();
            right[0] = qualifierType;
          }
          return (PsiMethod)element;
        }
      }
    }
    return null;
  }
}
