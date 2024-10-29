// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.magicConstant;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class MagicCompletionContributor extends CompletionContributor implements DumbAware {
  private static final ElementPattern<PsiElement> IN_METHOD_CALL_ARGUMENT =
    PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(PsiReferenceExpression.class).inside(
      PlatformPatterns.psiElement(PsiExpressionList.class).withParent(PsiCall.class)));
  private static final ElementPattern<PsiElement> IN_BINARY_COMPARISON =
    PlatformPatterns.psiElement().withParent(
      PlatformPatterns.psiElement(PsiReferenceExpression.class).inside(PlatformPatterns.psiElement(PsiPolyadicExpression.class)));
  private static final ElementPattern<PsiElement> IN_ASSIGNMENT =
    PlatformPatterns.psiElement().withParent(
      PlatformPatterns.psiElement(PsiReferenceExpression.class).inside(PlatformPatterns.psiElement(PsiAssignmentExpression.class)));
  private static final ElementPattern<PsiElement> IN_VARIABLE =
    PlatformPatterns.psiElement().withParent(
      PlatformPatterns.psiElement(PsiReferenceExpression.class).withParent(PlatformPatterns.psiElement(PsiVariable.class)));
  private static final ElementPattern<PsiElement> IN_RETURN =
    PlatformPatterns.psiElement().withParent(
      PlatformPatterns.psiElement(PsiReferenceExpression.class).inside(PlatformPatterns.psiElement(PsiReturnStatement.class)));
  private static final ElementPattern<PsiElement> IN_ANNOTATION_INITIALIZER =
    PlatformPatterns
      .psiElement().afterLeaf("=").withParent(PsiReferenceExpression.class).withSuperParent(2, PsiNameValuePair.class).withSuperParent(3, PsiAnnotationParameterList.class).withSuperParent(4, PsiAnnotation.class);
  private static final int PRIORITY = 100;

  @Override
  public void fillCompletionVariants(final @NotNull CompletionParameters parameters, final @NotNull CompletionResultSet result) {
    //if (parameters.getCompletionType() != CompletionType.SMART) return;
    PsiElement pos = parameters.getPosition();
    if (JavaKeywordCompletion.AFTER_DOT.accepts(pos)) {
      return;
    }

    MagicConstantUtils.AllowedValues allowedValues = getAllowedValues(pos);
    if (allowedValues == null) return;

    addCompletionVariants(parameters, result, pos, allowedValues);
  }

  public static @Nullable MagicConstantUtils.AllowedValues getAllowedValues(@NotNull PsiElement pos) {
    MagicConstantUtils.AllowedValues allowedValues = null;
    for (Pair<PsiModifierListOwner, PsiType> pair : getMembersWithAllowedValues(pos)) {
      MagicConstantUtils.AllowedValues values = MagicConstantUtils.getAllowedValues(pair.first, pair.second, pos);
      if (values == null) continue;
      if (allowedValues == null) {
        allowedValues = values;
        continue;
      }
      if (!allowedValues.equals(values)) return null;
    }
    return allowedValues;
  }

  private static @Nullable PsiModifierListOwner resolveExpression(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression)expression).resolveMethod();
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
      if (resolved instanceof PsiModifierListOwner) {
        return (PsiModifierListOwner)resolved;
      }
    }
    return null;
  }

  public static @NotNull List<Pair<PsiModifierListOwner, PsiType>> getMembersWithAllowedValues(@NotNull PsiElement pos) {
    Set<Pair<PsiModifierListOwner, PsiType>> result = new HashSet<>();
    if (IN_METHOD_CALL_ARGUMENT.accepts(pos)) {
      PsiCall call = PsiTreeUtil.getParentOfType(pos, PsiCall.class);
      if (call == null) return Collections.emptyList();

      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(call.getProject()).getResolveHelper();
      JavaResolveResult[] methods = getMethodCandidates(call, resolveHelper);
      for (JavaResolveResult resolveResult : methods) {
        PsiElement element = resolveResult.getElement();
        if (!(element instanceof PsiMethod method)) return Collections.emptyList();
        if (!resolveHelper.isAccessible(method, call, null)) continue;
        PsiElement argument = pos;
        while (!(argument.getContext() instanceof PsiExpressionList list)) argument = argument.getContext();
        int i = ArrayUtil.indexOf(list.getExpressions(), argument);
        if (i == -1) continue;
        PsiParameter[] params = method.getParameterList().getParameters();
        PsiParameter parameter;
        PsiType parameterType;
        if (method.isVarArgs() && i >= params.length - 1) {
          parameter = ArrayUtil.getLastElement(params);
          parameterType = ((PsiEllipsisType)parameter.getType()).getComponentType();
        }
        else if (i < params.length) {
          parameter = params[i];
          parameterType = parameter.getType();
        }
        else {
          continue;
        }
        result.add(Pair.create(parameter, parameterType));
      }
    }
    else if (IN_BINARY_COMPARISON.accepts(pos)) {
      PsiPolyadicExpression exp = PsiTreeUtil.getParentOfType(pos, PsiPolyadicExpression.class);
      if (exp != null && (exp.getOperationTokenType() == JavaTokenType.EQEQ || exp.getOperationTokenType() == JavaTokenType.NE)) {
        for (PsiExpression operand : exp.getOperands()) {
          PsiModifierListOwner resolved = resolveExpression(operand);
          if (resolved != null) {
            result.add(Pair.create(resolved, operand.getType()));
            // if something interesting assigned to this variable, e.g. magic method, suggest its magic too
            MagicConstantInspection.processValuesFlownTo(operand, pos.getContainingFile(), pos.getManager(), true, expression -> {
              PsiModifierListOwner assigned = resolveExpression(expression);
              if (assigned != null) {
                result.add(Pair.create(assigned, operand.getType()));
              }
              return true;
            });
          }
        }
      }
    }
    else if (IN_ASSIGNMENT.accepts(pos)) {
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(pos, PsiAssignmentExpression.class);
      PsiExpression l = assignment == null ? null : assignment.getLExpression();
      PsiModifierListOwner resolved = resolveExpression(l);
      if (resolved != null && PsiTreeUtil.isAncestor(assignment.getRExpression(), pos, false)) {
        result.add(Pair.create(resolved, l.getType()));
      }
    }
    else if (IN_VARIABLE.accepts(pos)) {
      PsiVariable variable = PsiTreeUtil.getParentOfType(pos, PsiVariable.class);
      result.add(Pair.create(variable, variable.getType()));
    }
    else if (IN_RETURN.accepts(pos)) {
      PsiReturnStatement statement = PsiTreeUtil.getParentOfType(pos, PsiReturnStatement.class);
      PsiExpression l = statement == null ? null : statement.getReturnValue();
      PsiElement element = PsiTreeUtil.getParentOfType(l, PsiMethod.class, PsiLambdaExpression.class);
      if (element instanceof PsiMethod) {
        result.add(Pair.create((PsiModifierListOwner)element, ((PsiMethod)element).getReturnType()));
      }
      else if (element instanceof PsiLambdaExpression) {
        final PsiType interfaceType = ((PsiLambdaExpression)element).getFunctionalInterfaceType();
        final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interfaceType);
        if (interfaceMethod != null) {
          result.add(Pair.create(interfaceMethod, LambdaUtil.getFunctionalInterfaceReturnType(interfaceType)));
        }
      }
    }
    else if (IN_ANNOTATION_INITIALIZER.accepts(pos)) {
      PsiNameValuePair pair = (PsiNameValuePair)pos.getParent().getParent();
      if (pair.getValue() instanceof PsiExpression) {
        PsiReference ref = pair.getReference();
        PsiMethod method = ref == null ? null : (PsiMethod)ref.resolve();
        if (method != null) {
          result.add(Pair.create(method, method.getReturnType()));
        }
      }
    }
    return new ArrayList<>(result);
  }

  private static @NotNull JavaResolveResult @NotNull [] getMethodCandidates(PsiCall call, PsiResolveHelper resolveHelper) {
    if (call instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression)call).getMethodExpression().multiResolve(true);
    }
    if (call instanceof PsiNewExpression) {
      PsiType type = ((PsiExpression)call).getType();
      PsiExpressionList argumentList = call.getArgumentList();
      if (type instanceof PsiClassType && argumentList != null) {
        return resolveHelper.multiResolveConstructor((PsiClassType)type, argumentList, call);
      }
    }
    if (call instanceof PsiEnumConstant) {
      JavaResolveResult result = call.resolveMethodGenerics();
      if (result != JavaResolveResult.EMPTY) {
        return new JavaResolveResult[]{result};
      }
    }
    return JavaResolveResult.EMPTY_ARRAY;
  }

  private static void addCompletionVariants(final @NotNull CompletionParameters parameters,
                                            final @NotNull CompletionResultSet result,
                                            PsiElement pos,
                                            MagicConstantUtils.AllowedValues allowedValues) {
    final Set<PsiElement> allowed = CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
      @Override
      public int hashCode(PsiElement object) {
        return 0;
      }

      @Override
      public boolean equals(PsiElement o1, PsiElement o2) {
        return parameters.getOriginalFile().getManager().areElementsEquivalent(o1, o2);
      }
    });
    if (allowedValues.isFlagSet()) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(pos.getProject());
      PsiExpression zero = factory.createExpressionFromText("0", pos);
      result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(zero, "0"), PRIORITY - 1));
      PsiExpression minusOne = factory.createExpressionFromText("-1", pos);
      result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(minusOne,"-1"), PRIORITY-1));
      allowed.add(zero);
      allowed.add(minusOne);
    }
    List<ExpectedTypeInfo> types = Arrays.asList(JavaSmartCompletionContributor.getExpectedTypes(parameters));
    for (PsiAnnotationMemberValue value : allowedValues.getValues()) {
      if (value instanceof PsiReference) {
        PsiElement resolved = ((PsiReference)value).resolve();
        if (resolved instanceof PsiNamedElement) {

          LookupElement lookupElement = LookupItemUtil.objectToLookupItem(resolved);
          if (lookupElement instanceof VariableLookupItem) {
            ((VariableLookupItem)lookupElement).setSubstitutor(PsiSubstitutor.EMPTY);
          }
          LookupElement element = PrioritizedLookupElement.withPriority(lookupElement, PRIORITY);
          element = decorate(parameters, types, element);
          result.addElement(element);
          allowed.add(resolved);
          continue;
        }
      }
      LookupElement element = LookupElementBuilder.create(value, value.getText());
      element = decorate(parameters, types, element);
      result.addElement(element);
      allowed.add(value);
    }

    result.runRemainingContributors(parameters, completionResult -> {
      LookupElement element = completionResult.getLookupElement();
      Object object = element.getObject();
      if (object instanceof PsiElement && allowed.contains(object)) {
        return;
      }
      result.passResult(completionResult);
    });
  }

  private static LookupElement decorate(CompletionParameters parameters, List<? extends ExpectedTypeInfo> types, LookupElement element) {
    if (!types.isEmpty() && parameters.getCompletionType() == CompletionType.SMART) {
      element = JavaSmartCompletionContributor.decorate(element, types);
    }
    return element;
  }
}


