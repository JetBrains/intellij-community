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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FunctionalInterfaceSuggester {
  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiFunctionalExpression expression) {

    final Project project = expression.getProject();
    final PsiClass functionalInterfaceClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, GlobalSearchScope.allScope(project));
    if (functionalInterfaceClass == null) {
      return Collections.emptyList();
    }
    final Set<PsiType> types = new LinkedHashSet<PsiType>();
    AnnotatedMembersSearch.search(functionalInterfaceClass, expression.getResolveScope()).forEach(new Processor<PsiMember>() {
      @Override
      public boolean process(PsiMember member) {
        if (member instanceof PsiClass) {
          ContainerUtil.addIfNotNull(types, composeAcceptableType((PsiClass)member, expression));
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
