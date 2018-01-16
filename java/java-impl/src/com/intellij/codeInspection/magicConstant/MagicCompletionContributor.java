/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection.magicConstant;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class MagicCompletionContributor extends CompletionContributor {
  private static final ElementPattern<PsiElement> IN_METHOD_CALL_ARGUMENT =
    psiElement().withParent(psiElement(PsiReferenceExpression.class).inside(psiElement(PsiExpressionList.class).withParent(PsiCall.class)));
  private static final ElementPattern<PsiElement> IN_BINARY_COMPARISON =
    psiElement().withParent(psiElement(PsiReferenceExpression.class).inside(psiElement(PsiPolyadicExpression.class)));
  private static final ElementPattern<PsiElement> IN_ASSIGNMENT =
    psiElement().withParent(psiElement(PsiReferenceExpression.class).inside(psiElement(PsiAssignmentExpression.class)));
  private static final ElementPattern<PsiElement> IN_RETURN =
    psiElement().withParent(psiElement(PsiReferenceExpression.class).inside(psiElement(PsiReturnStatement.class)));
  private static final ElementPattern<PsiElement> IN_ANNOTATION_INITIALIZER =
    psiElement().afterLeaf("=").withParent(PsiReferenceExpression.class).withSuperParent(2,PsiNameValuePair.class).withSuperParent(3,PsiAnnotationParameterList.class).withSuperParent(4,PsiAnnotation.class);
  private static final int PRIORITY = 100;

  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    //if (parameters.getCompletionType() != CompletionType.SMART) return;
    PsiElement pos = parameters.getPosition();
    if (JavaKeywordCompletion.AFTER_DOT.accepts(pos)) {
      return;
    }

    MagicConstantInspection.AllowedValues allowedValues = getAllowedValues(pos);
    if (allowedValues == null) return;

    addCompletionVariants(parameters, result, pos, allowedValues);
  }

  @Nullable
  public static MagicConstantInspection.AllowedValues getAllowedValues(@NotNull PsiElement pos) {
    MagicConstantInspection.AllowedValues allowedValues = null;
    for (Pair<PsiModifierListOwner, PsiType> pair : getMembersWithAllowedValues(pos)) {
      MagicConstantInspection.AllowedValues values = MagicConstantInspection.getAllowedValues(pair.first, pair.second, null);
      if (values == null) continue;
      if (allowedValues == null) {
        allowedValues = values;
        continue;
      }
      if (!allowedValues.equals(values)) return null;
    }
    return allowedValues;
  }

  @Nullable
  private static PsiModifierListOwner resolveExpression(@Nullable PsiExpression expression) {
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

  @NotNull
  public static List<Pair<PsiModifierListOwner, PsiType>> getMembersWithAllowedValues(@NotNull PsiElement pos) {
    Set<Pair<PsiModifierListOwner, PsiType>> result = new THashSet<>();
    if (IN_METHOD_CALL_ARGUMENT.accepts(pos)) {
      PsiCall call = PsiTreeUtil.getParentOfType(pos, PsiCall.class);
      if (!(call instanceof PsiExpression)) return Collections.emptyList();
      PsiType type = ((PsiExpression)call).getType();

      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(call.getProject()).getResolveHelper();
      JavaResolveResult[] methods = call instanceof PsiMethodCallExpression
                                    ? ((PsiMethodCallExpression)call).getMethodExpression().multiResolve(true)
                                    : call instanceof PsiNewExpression && type instanceof PsiClassType
                                      ? resolveHelper.multiResolveConstructor((PsiClassType)type, call.getArgumentList(), call)
                                      : JavaResolveResult.EMPTY_ARRAY;
      for (JavaResolveResult resolveResult : methods) {
        PsiElement element = resolveResult.getElement();
        if (!(element instanceof PsiMethod)) return Collections.emptyList();
        PsiMethod method = (PsiMethod)element;
        if (!resolveHelper.isAccessible(method, call, null)) continue;
        PsiElement argument = pos;
        while (!(argument.getContext() instanceof PsiExpressionList)) argument = argument.getContext();
        PsiExpressionList list = (PsiExpressionList)argument.getContext();
        int i = ArrayUtil.indexOf(list.getExpressions(), argument);
        if (i == -1) continue;
        PsiParameter[] params = method.getParameterList().getParameters();
        if (i >= params.length) continue;
        PsiParameter parameter = params[i];
        result.add(Pair.create(parameter, parameter.getType()));
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
            MagicConstantInspection.processValuesFlownTo(operand, pos.getContainingFile(), pos.getManager(), expression -> {
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
      PsiElement resolved = resolveExpression(l);
      if (resolved != null && PsiTreeUtil.isAncestor(assignment.getRExpression(), pos, false)) {
        result.add(Pair.create((PsiModifierListOwner)resolved, l.getType()));
      }
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
          result.add(Pair.create((PsiModifierListOwner)interfaceMethod, LambdaUtil.getFunctionalInterfaceReturnType(interfaceType)));
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

  private static void addCompletionVariants(@NotNull final CompletionParameters parameters,
                                            @NotNull final CompletionResultSet result,
                                            PsiElement pos,
                                            MagicConstantInspection.AllowedValues allowedValues) {
    final Set<PsiElement> allowed = new THashSet<>(new TObjectHashingStrategy<PsiElement>() {
      @Override
      public int computeHashCode(PsiElement object) {
        return 0;
      }

      @Override
      public boolean equals(PsiElement o1, PsiElement o2) {
        return parameters.getOriginalFile().getManager().areElementsEquivalent(o1, o2);
      }
    });
    if (allowedValues.canBeOred) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(pos.getProject());
      PsiExpression zero = factory.createExpressionFromText("0", pos);
      result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(zero, "0"), PRIORITY - 1));
      PsiExpression minusOne = factory.createExpressionFromText("-1", pos);
      result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(minusOne,"-1"), PRIORITY-1));
      allowed.add(zero);
      allowed.add(minusOne);
    }
    List<ExpectedTypeInfo> types = Arrays.asList(JavaSmartCompletionContributor.getExpectedTypes(parameters));
    for (PsiAnnotationMemberValue value : allowedValues.values) {
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

  private static LookupElement decorate(CompletionParameters parameters, List<ExpectedTypeInfo> types, LookupElement element) {
    if (!types.isEmpty() && parameters.getCompletionType() == CompletionType.SMART) {
      element = JavaSmartCompletionContributor.decorate(element, types);
    }
    return element;
  }
}


