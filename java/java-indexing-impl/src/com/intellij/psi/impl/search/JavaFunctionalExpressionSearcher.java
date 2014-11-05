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
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaMethodElementType;
import com.intellij.psi.impl.java.stubs.index.JavaMethodParameterTypesIndex;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class JavaFunctionalExpressionSearcher implements QueryExecutor<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters> {

  @Override
  public boolean execute(@NotNull final FunctionalExpressionSearch.SearchParameters queryParameters,
                         @NotNull final Processor<PsiFunctionalExpression> consumer) {
    final PsiClass aClass = queryParameters.getElementToSearch();
    final Set<Module> highLevelModules = new HashSet<Module>();
    if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (LambdaUtil.isFunctionalClass(aClass)) {
          final Project project = aClass.getProject();
          final boolean projectLevelIsHigh = PsiUtil.getLanguageLevel(project).isAtLeast(LanguageLevel.JDK_1_8);

          for (Module module : ModuleManager.getInstance(project).getModules()) {
            final LanguageLevelModuleExtension extension = ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
            if (extension != null) {
              final LanguageLevel level = extension.getLanguageLevel();
              if (level == null && projectLevelIsHigh || level != null && level.isAtLeast(LanguageLevel.JDK_1_8)) {
                highLevelModules.add(module);
              }
            }
          }
          return highLevelModules.isEmpty();
        }
        return true;
      }
    })) {
      return true;
    }
    return collectFunctionalExpressions(aClass, ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        return queryParameters.getEffectiveSearchScope();
      }
    }), consumer, highLevelModules);
  }

  public static boolean collectFunctionalExpressions(final PsiClass aClass,
                                                     final SearchScope searchScope,
                                                     final Processor<PsiFunctionalExpression> consumer, 
                                                     final Set<Module> highLevelModules) {
    final SearchScope classScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        return aClass.getUseScope();
      }
    });
    final SearchScope useScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        return searchScope.intersectWith(classScope);
      }
    });
    final Project project = PsiUtilCore.getProjectInReadAction(aClass);
    final GlobalSearchScope scope = new ModulesScope(highLevelModules, project)
      .intersectWith(useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : new EverythingGlobalScope(project));
    final Collection<PsiMethod> lambdaCandidates = ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiMethod>>() {
      @Override
      public Collection<PsiMethod> compute() {
        final String functionalInterfaceName = aClass.getName();
        final GlobalSearchScope useClassScope = classScope instanceof GlobalSearchScope ? (GlobalSearchScope)classScope : scope;
        JavaMethodParameterTypesIndex parameterTypesIndex = JavaMethodParameterTypesIndex.getInstance();
        LinkedHashSet<PsiMethod> methods = new LinkedHashSet<PsiMethod>(parameterTypesIndex.get(functionalInterfaceName, project, useClassScope));
        methods.addAll(parameterTypesIndex.get(JavaMethodElementType.TYPE_PARAMETER_PSEUDO_NAME, project,
                                               GlobalSearchScope.allScope(project)));
        return methods;
      }
    });
    for (PsiMethod psiMethod : lambdaCandidates) {
      for (final PsiReference ref : MethodReferencesSearch.search(psiMethod, scope, false)) {
        boolean accepted = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            final PsiElement refElement = ref.getElement();
            if (refElement != null) {
              final PsiElement candidateElement = refElement.getParent();
              if (candidateElement instanceof PsiCallExpression) {
                final PsiExpressionList argumentList = ((PsiCallExpression)candidateElement).getArgumentList();
                if (argumentList != null) {
                  final PsiExpression[] args = argumentList.getExpressions();
                  for (PsiExpression arg : args) {
                    if (arg instanceof PsiFunctionalExpression) {
                      final PsiFunctionalExpression functionalExpression = (PsiFunctionalExpression)arg;
                      final PsiType functionalType = functionalExpression.getFunctionalInterfaceType();
                      if (PsiUtil.resolveClassInType(functionalType) == aClass) {
                        if (!consumer.process(functionalExpression)) return false;
                      }
                    }
                  }
                }
              }
            }
            return true;
          }
        });
        if (!accepted) return false;
      }
    }

    for (final PsiReference reference : ReferencesSearch.search(aClass, scope)) {
      boolean accepted = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          final PsiElement element = reference.getElement();
          if (element != null) {
            final PsiElement parent = element.getParent();
            if (parent instanceof PsiTypeElement) {
              final PsiElement gParent = parent.getParent();
              if (gParent instanceof PsiVariable) {
                final PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(((PsiVariable)gParent).getInitializer());
                if (initializer instanceof PsiFunctionalExpression) {
                  if (!consumer.process((PsiFunctionalExpression)initializer)) return false;
                }
                for (PsiReference varRef : ReferencesSearch.search(parent, scope)) {
                  final PsiElement varElement = varRef.getElement();
                  if (varElement != null) {
                    final PsiElement varElementParent = varElement.getParent();
                    if (varElementParent instanceof PsiAssignmentExpression &&
                        ((PsiAssignmentExpression)varElementParent).getLExpression() == varElement) {
                      final PsiExpression rExpression = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)varElementParent).getRExpression());
                      if (rExpression instanceof PsiFunctionalExpression) {
                        if (!consumer.process((PsiFunctionalExpression)rExpression)) return false;
                      }
                    }
                  }
                }
              } else if (gParent instanceof PsiMethod) {
                final PsiReturnStatement[] returnStatements = ApplicationManager.getApplication().runReadAction(
                  new Computable<PsiReturnStatement[]>() {
                    @Override
                    public PsiReturnStatement[] compute() {
                      return PsiUtil.findReturnStatements((PsiMethod)gParent);
                    }
                  });
                for (PsiReturnStatement returnStatement : returnStatements) {
                  final PsiExpression returnValue = returnStatement.getReturnValue();
                  if (returnValue instanceof PsiFunctionalExpression) {
                    if (!consumer.process((PsiFunctionalExpression)returnValue)) return false;
                  }
                }
              }
            }
          }

          return true;
        }
      });
      if (!accepted) return false;

    }
    return true;
  }
}
