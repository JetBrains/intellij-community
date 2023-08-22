// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

final class SliceForwardUtil {
  static boolean processUsagesFlownFromThe(@NotNull PsiElement element,
                                           @NotNull final JavaSliceUsage parent,
                                           @NotNull final Processor<? super SliceUsage> processor) {
    PsiExpression expression = getMethodCallTarget(element);
    JavaSliceBuilder builder = JavaSliceBuilder.create(parent).dropSyntheticField();
    if (expression != null && !builder.process(expression, processor)) {
      return false;
    }
    Pair<PsiElement, PsiSubstitutor> pair = getAssignmentTarget(element, parent);
    if (pair != null) {
      PsiElement target = pair.getFirst();
      if (target instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
        final PsiSubstitutor substitutor = pair.getSecond();
        final int parameterIndex = method.getParameterList().getParameterIndex(parameter);

        Processor<PsiMethod> myProcessor = override -> {
          if (!parent.getScope().contains(override)) return true;
          final PsiSubstitutor superSubstitutor = method == override
                                                  ? substitutor
                                                  : MethodSignatureUtil.getSuperMethodSignatureSubstitutor(method.getSignature(substitutor),
                                                                                                           override.getSignature(
                                                                                                             substitutor));

          PsiParameter[] parameters = override.getParameterList().getParameters();
          if (parameters.length <= parameterIndex || superSubstitutor == null) return true;
          PsiParameter actualParam = parameters[parameterIndex];

          return builder.withSubstitutor(superSubstitutor).process(actualParam, processor);
        };
        if (!myProcessor.process(method)) return false;
        return OverridingMethodsSearch.search(method, parent.getScope().toSearchScope(), true).forEach(myProcessor);
      }

      return builder.process(target, processor);
    }

    if (element instanceof PsiReferenceExpression ref) {
      PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiVariable variable)) return true;
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
                                             @NotNull final Processor<? super SliceUsage> processor) {
    if (from instanceof PsiLocalVariable) {
      return searchReferencesAndProcessAssignmentTarget(from, context, parent, processor);
    }
    if (from instanceof PsiParameter parameter) {
      PsiElement scope = parameter.getDeclarationScope();
      Collection<PsiParameter> parametersToAnalyze = new HashSet<>();
      if (scope instanceof PsiMethod method && ((PsiMethod)scope).hasModifierProperty(PsiModifier.ABSTRACT)) {
        int index = method.getParameterList().getParameterIndex(parameter);
        final Set<PsiMethod> implementors = new HashSet<>();

        if (!OverridingMethodsSearch.search(method, parent.getScope().toSearchScope(), true).forEach(sub -> {
          ProgressManager.checkCanceled();
          implementors.add(sub);
          return true;
        })) return false;
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
    else if (from instanceof PsiField) {
      return searchReferencesAndProcessAssignmentTarget(from, null, parent, processor);
    }

    if (from instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)from;

      Collection<PsiMethod> superMethods = ContainerUtil.newHashSet(method.findDeepestSuperMethods());
      superMethods.add(method);
      final Set<PsiReference> processed = new HashSet<>(); //usages of super method and overridden method can overlap
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
                                                                    @NotNull Processor<? super SliceUsage> processor) {
    return ReferencesSearch.search(element).forEach(reference -> {
      PsiElement element1 = reference.getElement();
      if (context != null && element1.getTextOffset() < context.getTextOffset()) return true;
      return processAssignmentTarget(element1, parent, processor);
    });
  }

  private static boolean processAssignmentTarget(@NotNull PsiElement element,
                                                 @NotNull JavaSliceUsage parent,
                                                 @NotNull Processor<? super SliceUsage> processor) {
    if (!parent.params.scope.contains(element)) return true;
    if (element instanceof PsiCompiledElement) element = element.getNavigationElement();
    if (element.getLanguage() != JavaLanguage.INSTANCE) {
      return JavaSliceBuilder.create(parent).withSubstitutor(EmptySubstitutor.getInstance()).dropSyntheticField().process(element, processor);
    }
    Pair<PsiElement, PsiSubstitutor> pair = getAssignmentTarget(element, parent);
    if (pair != null) {
      return JavaSliceBuilder.create(parent).withSubstitutor(pair.second).dropSyntheticField().process(element, processor);
    }
    if (parent.params.showInstanceDereferences && isDereferenced(element)) {
      SliceUsage usage = new JavaSliceDereferenceUsage(element.getParent(), parent, parent.getSubstitutor());
      return processor.process(usage);
    }
    return true;
  }

  private static PsiExpression getMethodCallTarget(PsiElement element) {
    element = complexify(element);
    PsiMethodCallExpression call = null;
    if (element.getParent() instanceof PsiExpressionList) {
      call = ObjectUtils.tryCast(element.getParent().getParent(), PsiMethodCallExpression.class);
    }
    PsiExpression value = JavaMethodContractUtil.findReturnedValue(call);
    return value == element ? call : null;
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
    if (parent instanceof PsiAssignmentExpression assignment) {
      if (element.equals(assignment.getRExpression())) {
        PsiElement left = assignment.getLExpression();
        if (left instanceof PsiReferenceExpression) {
          JavaResolveResult result = ((PsiReferenceExpression)left).advancedResolve(false);
          target = result.getElement();
          substitutor = result.getSubstitutor();
        }
      }
    }
    else if (parent instanceof PsiVariable variable) {

      PsiElement initializer = variable.getInitializer();
      if (element.equals(initializer)) {
        target = variable;
      }
    }
    //method call
    else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression methodCall) {
      PsiExpression[] expressions = ((PsiExpressionList)parent).getExpressions();
      int index = ArrayUtilRt.find(expressions, element);
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
    else if (parent instanceof PsiReturnStatement statement) {
      if (element.equals(statement.getReturnValue())) {
        target = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      }
    }
    else if (element instanceof PsiExpression){
      PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier((PsiExpression)element);
      PsiExpression maybeQualifier = JavaMethodContractUtil.findReturnedValue(call);
      if (maybeQualifier == element) {
        target = call;
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
