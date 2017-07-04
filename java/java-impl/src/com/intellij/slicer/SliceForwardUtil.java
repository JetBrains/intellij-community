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
package com.intellij.slicer;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * @author cdr
 */
class SliceForwardUtil {
  static boolean processUsagesFlownFromThe(@NotNull PsiElement element,
                                           @NotNull final JavaSliceUsage parent,
                                           @NotNull final Processor<SliceUsage> processor) {
    Pair<PsiElement, PsiSubstitutor> pair = getAssignmentTarget(element, parent);
    if (pair != null) {
      PsiElement target = pair.getFirst();
      final PsiSubstitutor substitutor = pair.getSecond();
      if (target instanceof PsiParameter) {
        PsiParameter parameter = (PsiParameter)target;
        PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          final int parameterIndex = method.getParameterList().getParameterIndex(parameter);

          Processor<PsiMethod> myProcessor = override -> {
            if (!parent.getScope().contains(override)) return true;
            final PsiSubstitutor superSubstitutor = method == override
                                                    ? substitutor
                                                    : MethodSignatureUtil.getSuperMethodSignatureSubstitutor(method.getSignature(substitutor),
                                                                                          override.getSignature(substitutor));

            PsiParameter[] parameters = override.getParameterList().getParameters();
            if (parameters.length <= parameterIndex) return true;
            PsiParameter actualParam = parameters[parameterIndex];

            SliceUsage usage = SliceUtil.createSliceUsage(actualParam, parent, superSubstitutor,parent.indexNesting, "");
            return processor.process(usage);
          };
          if (!myProcessor.process(method)) return false;
          return OverridingMethodsSearch.search(method, parent.getScope().toSearchScope(), true).forEach(myProcessor);
        }
      }

      SliceUsage usage = SliceUtil.createSliceUsage(target, parent, parent.getSubstitutor(),parent.indexNesting, "");
      return processor.process(usage);
    }

    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)element;
      PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiVariable)) return true;
      final PsiVariable variable = (PsiVariable)resolved;
      return processAssignedFrom(variable, ref, parent, processor);
    }
    if (element instanceof PsiVariable) {
      return processAssignedFrom(element, element, parent, processor);
    }
    if (element instanceof PsiMethod) {
      return processAssignedFrom(element, element, parent, processor);
    }
    return true;
  }

  private static boolean processAssignedFrom(@NotNull PsiElement from,
                                             @NotNull PsiElement context,
                                             @NotNull JavaSliceUsage parent,
                                             @NotNull final Processor<SliceUsage> processor) {
    if (from instanceof PsiLocalVariable) {
      return searchReferencesAndProcessAssignmentTarget(from, context, parent, processor);
    }
    if (from instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)from;
      PsiElement scope = parameter.getDeclarationScope();
      Collection<PsiParameter> parametersToAnalyze = new THashSet<>();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        int index = method.getParameterList().getParameterIndex(parameter);

        Collection<PsiMethod> superMethods = new THashSet<>(Arrays.asList(method.findDeepestSuperMethods()));
        superMethods.add(method);
        for (Iterator<PsiMethod> iterator = superMethods.iterator(); iterator.hasNext(); ) {
          ProgressManager.checkCanceled();
          PsiMethod superMethod = iterator.next();
          if (!parent.params.scope.contains(superMethod)) {
            iterator.remove();
          }
        }

        final THashSet<PsiMethod> implementors = new THashSet<>(superMethods);
        for (PsiMethod superMethod : superMethods) {
          ProgressManager.checkCanceled();
          if (!OverridingMethodsSearch.search(superMethod, parent.getScope().toSearchScope(), true).forEach(sub -> {
            ProgressManager.checkCanceled();
            implementors.add(sub);
            return true;
          })) return false;
        }
        for (PsiMethod implementor : implementors) {
          ProgressManager.checkCanceled();
          if (!parent.params.scope.contains(implementor)) continue;
          if (implementor instanceof PsiCompiledElement) implementor = (PsiMethod)implementor.getNavigationElement();

          PsiParameter[] parameters = implementor.getParameterList().getParameters();
          if (index != -1 && index < parameters.length) {
            parametersToAnalyze.add(parameters[index]);
          }
        }
      }
      else {
        parametersToAnalyze.add(parameter);
      }
      for (final PsiParameter psiParameter : parametersToAnalyze) {
        ProgressManager.checkCanceled();

        if (!searchReferencesAndProcessAssignmentTarget(psiParameter, null, parent, processor)) return false;
      }
      return true;
    }
    if (from instanceof PsiField) {
      return searchReferencesAndProcessAssignmentTarget(from, null, parent, processor);
    }

    if (from instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)from;

      Collection<PsiMethod> superMethods = new THashSet<>(Arrays.asList(method.findDeepestSuperMethods()));
      superMethods.add(method);
      final Set<PsiReference> processed = new THashSet<>(); //usages of super method and overridden method can overlap
      for (final PsiMethod containingMethod : superMethods) {
        if (!MethodReferencesSearch.search(containingMethod, parent.getScope().toSearchScope(), true).forEach(reference -> {
          ProgressManager.checkCanceled();
          synchronized (processed) {
            if (!processed.add(reference)) return true;
          }
          PsiElement element = reference.getElement().getParent();

          return processAssignmentTarget(element, parent, processor);
        })) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean searchReferencesAndProcessAssignmentTarget(@NotNull PsiElement element,
                                                                    @Nullable final PsiElement context,
                                                                    @NotNull JavaSliceUsage parent,
                                                                    @NotNull Processor<SliceUsage> processor) {
    return ReferencesSearch.search(element).forEach(reference -> {
      PsiElement element1 = reference.getElement();
      if (context != null && element1.getTextOffset() < context.getTextOffset()) return true;
      return processAssignmentTarget(element1, parent, processor);
    });
  }

  private static boolean processAssignmentTarget(@NotNull PsiElement element,
                                                 @NotNull JavaSliceUsage parent,
                                                 @NotNull Processor<SliceUsage> processor) {
    if (!parent.params.scope.contains(element)) return true;
    if (element instanceof PsiCompiledElement) element = element.getNavigationElement();
    if (element.getLanguage() != JavaLanguage.INSTANCE) {
      SliceUsage usage = SliceUtil.createSliceUsage(element, parent, EmptySubstitutor.getInstance(), parent.indexNesting, "");
      return processor.process(usage);
    }
    Pair<PsiElement, PsiSubstitutor> pair = getAssignmentTarget(element, parent);
    if (pair != null) {
      SliceUsage usage = SliceUtil.createSliceUsage(element, parent, pair.getSecond(),parent.indexNesting, "");
      return processor.process(usage);
    }
    if (parent.params.showInstanceDereferences && isDereferenced(element)) {
      SliceUsage usage = new JavaSliceDereferenceUsage(element.getParent(), parent, parent.getSubstitutor());
      return processor.process(usage);
    }
    return true;
  }

  private static boolean isDereferenced(@NotNull PsiElement element) {
    if (!(element instanceof PsiReferenceExpression)) return false;
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceExpression)) return false;
    return ((PsiReferenceExpression)parent).getQualifierExpression() == element;
  }

  private static Pair<PsiElement,PsiSubstitutor> getAssignmentTarget(@NotNull PsiElement element, @NotNull JavaSliceUsage parentUsage) {
    element = complexify(element);
    PsiElement target = null;
    PsiSubstitutor substitutor = parentUsage.getSubstitutor();
    //assignment
    PsiElement parent = element.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      if (element.equals(assignment.getRExpression())) {
        PsiElement left = assignment.getLExpression();
        if (left instanceof PsiReferenceExpression) {
          JavaResolveResult result = ((PsiReferenceExpression)left).advancedResolve(false);
          target = result.getElement();
          substitutor = result.getSubstitutor();
        }
      }
    }
    else if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;

      PsiElement initializer = variable.getInitializer();
      if (element.equals(initializer)) {
        target = variable;
      }
    }
    //method call
    else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression) {
      PsiExpression[] expressions = ((PsiExpressionList)parent).getExpressions();
      int index = ArrayUtilRt.find(expressions, element);
      PsiCallExpression methodCall = (PsiCallExpression)parent.getParent();
      JavaResolveResult result = methodCall.resolveMethodGenerics();
      PsiMethod method = (PsiMethod)result.getElement();
      if (index != -1 && method != null) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (index < parameters.length) {
          target = parameters[index];
          substitutor = result.getSubstitutor();
        }
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiReturnStatement statement = (PsiReturnStatement)parent;
      if (element.equals(statement.getReturnValue())) {
        target = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      }
    }

    return target == null ? null : Pair.create(target, substitutor);
  }

  @NotNull
  static PsiElement complexify(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiParenthesizedExpression && element.equals(((PsiParenthesizedExpression)parent).getExpression())) {
      return complexify(parent);
    }
    if (parent instanceof PsiTypeCastExpression && element.equals(((PsiTypeCastExpression)parent).getOperand())) {
      return complexify(parent);
    }
    return element;
  }
}
