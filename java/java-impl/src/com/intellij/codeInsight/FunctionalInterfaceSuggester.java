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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FunctionalInterfaceSuggester {
  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiFunctionalExpression expression) {
    return suggestFunctionalInterfaces(expression, new NullableFunction<PsiClass, PsiType>() {
      @Nullable
      @Override
      public PsiType fun(PsiClass aClass) {
        return composeAcceptableType(aClass, expression);
      }
    });
  }

  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiMethod method) {
    if (method.isConstructor()) {
      return Collections.emptyList();
    }

    return suggestFunctionalInterfaces(method, new NullableFunction<PsiClass, PsiType>() {
      @Nullable
      @Override
      public PsiType fun(PsiClass aClass) {
        final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass);
        if (interfaceMethod != null) {
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          final PsiParameter[] interfaceMethodParameters = interfaceMethod.getParameterList().getParameters();
          if (parameters.length != interfaceMethodParameters.length) {
            return null;
          }

          final PsiType[] left = new PsiType[parameters.length];
          final PsiType[] right = new PsiType[parameters.length];

          for (int i = 0; i < parameters.length; i++) {
            left[i]  = interfaceMethodParameters[i].getType();
            right[i] = parameters[i].getType();
          }

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
          if (!TypeConversionUtil.isAssignable(substitutor.substitute(interfaceMethod.getReturnType()), returnType)) {
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
          final PsiType type = elementFactory.createType(aClass, substitutor);
          return type;
        }
        return null;
      }
    });
  }

  private static <T extends PsiElement> Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull T element, final NullableFunction<PsiClass, PsiType> acceptanceChecker) {
    final Project project = element.getProject();
    final PsiClass functionalInterfaceClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE,
                                                                                           GlobalSearchScope.allScope(project));
    if (functionalInterfaceClass == null) {
      return Collections.emptyList();
    }
    final Set<PsiType> types = new LinkedHashSet<PsiType>();
    AnnotatedMembersSearch.search(functionalInterfaceClass, element.getResolveScope()).forEach(new Processor<PsiMember>() {
      @Override
      public boolean process(PsiMember member) {
        if (member instanceof PsiClass && !Java15APIUsageInspectionBase.isForbiddenApiUsage(member, PsiUtil.getLanguageLevel(element))) {
          ContainerUtil.addIfNotNull(types, acceptanceChecker.fun((PsiClass)member));
        }
        return true;
      }
    });
    return types;
  }

  private static PsiType composeAcceptableType(@NotNull PsiClass interface2Consider, @NotNull PsiFunctionalExpression expression) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(interface2Consider.getProject());
    final PsiType type = elementFactory.createType(interface2Consider, PsiSubstitutor.EMPTY);
    if (expression.isAcceptable(type)) {
      return type;
    }

    return composeAcceptableType(interface2Consider, expression, elementFactory);
  }

  private static PsiType composeAcceptableType(final PsiClass interface2Consider,
                                               final PsiFunctionalExpression expression,
                                               final PsiElementFactory elementFactory) {

    if (interface2Consider.hasTypeParameters()) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interface2Consider);
      if (interfaceMethod != null) {
        final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
        final PsiParameter[] functionalExprParameters;
        if (expression instanceof PsiLambdaExpression && ((PsiLambdaExpression)expression).hasFormalParameterTypes()) {
          functionalExprParameters = ((PsiLambdaExpression)expression).getParameterList().getParameters();
        }
        else if (expression instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)expression).isExact()) {
          final PsiElement exactMethod = ((PsiMethodReferenceExpression)expression).resolve();
          if (!(exactMethod instanceof PsiMethod)) {
            return null;
          }
          functionalExprParameters = ((PsiMethod)exactMethod).getParameterList().getParameters();
        } else {
          return null;
        }

        if (parameters.length != functionalExprParameters.length) {
          return null;
        }

        final PsiType[] left = new PsiType[parameters.length];
        final PsiType[] right = new PsiType[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
          left[i]  = parameters[i].getType();
          right[i] = functionalExprParameters[i].getType();
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
}
