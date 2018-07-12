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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public abstract class ArgumentFixerActionFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.ArgumentFixerActionFactory");

  @Nullable
  protected abstract PsiExpression getModifiedArgument(PsiExpression expression, final PsiType toType) throws IncorrectOperationException;

  public void registerCastActions(@NotNull CandidateInfo[] candidates, @NotNull PsiCall call, HighlightInfo highlightInfo, final TextRange fixRange) {
    if (candidates.length == 0) return;
    List<CandidateInfo> methodCandidates = new ArrayList<>(Arrays.asList(candidates));
    PsiExpressionList list = call.getArgumentList();
    if (list == null) return;
    PsiExpression[] expressions = list.getExpressions();
    if (expressions.length == 0) return;
    // filter out not cast-able candidates
    nextMethod:
    for (int i = methodCandidates.size() - 1; i >= 0; i--) {
      CandidateInfo candidate = methodCandidates.get(i);
      PsiMethod method = (PsiMethod) candidate.getElement();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (expressions.length != parameters.length && !method.isVarArgs()) {
        methodCandidates.remove(i);
        continue;
      }
      for (int j = 0; j < Math.min(parameters.length, expressions.length); j++) {
        PsiParameter parameter = parameters[j];
        PsiExpression expression = expressions[j];
        // check if we can cast to this method
        PsiType exprType = expression.getType();
        PsiType parameterType = substitutor.substitute(parameter.getType());
        if (exprType == null
            || parameterType == null
            || !areTypesConvertible(exprType, parameterType, call)) {
          methodCandidates.remove(i);
          continue nextMethod;
        }
      }
    }

    if (methodCandidates.isEmpty()) return;

    try {
      PsiType expectedTypeByParent = PsiTypesUtil.getExpectedTypeByParent(call);
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression expression = expressions[i];
        PsiType exprType = expression.getType();
        Set<String> suggestedCasts = new THashSet<>();
        // find to which type we can cast this param to get valid method call
        for (CandidateInfo candidate : methodCandidates) {
          PsiMethod method = (PsiMethod)candidate.getElement();
          PsiSubstitutor substitutor = candidate.getSubstitutor();
          PsiType originalParameterType = PsiTypesUtil.getParameterType(method.getParameterList().getParameters(), i, true);
          PsiType parameterType = substitutor.substitute(originalParameterType);
          if (parameterType instanceof PsiWildcardType) continue;
          if (!GenericsUtil.isFromExternalTypeLanguage(parameterType)) continue;
          if (suggestedCasts.contains(parameterType.getCanonicalText())) continue;
          if (TypeConversionUtil.isPrimitiveAndNotNull(exprType) && parameterType instanceof PsiClassType) {
            PsiType unboxedParameterType = PsiPrimitiveType.getUnboxedType(parameterType);
            if (unboxedParameterType != null) {
              parameterType = unboxedParameterType;
            }
          }
          // strict compare since even widening cast may help
          if (Comparing.equal(exprType, parameterType)) continue;
          PsiCall newCall = LambdaUtil.copyTopLevelCall(call); //copy with expected type
          if (newCall == null) continue;
          PsiExpression modifiedExpression = getModifiedArgument(expression, parameterType);
          if (modifiedExpression == null) continue;
          PsiExpressionList argumentList = newCall.getArgumentList();
          if (argumentList == null) continue;
          argumentList.getExpressions()[i].replace(modifiedExpression);
          JavaResolveResult resolveResult = newCall.resolveMethodGenerics();
          if (resolveResult.getElement() != null && resolveResult.isValidResult()) {
            if (expectedTypeByParent != null && newCall instanceof PsiCallExpression) {
              PsiType type = ((PsiCallExpression)newCall).getType();
              if (type != null && !TypeConversionUtil.isAssignable(expectedTypeByParent, type)) continue;
            }
            suggestedCasts.add(parameterType.getCanonicalText());
            QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, createFix(list, i, parameterType));
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public abstract boolean areTypesConvertible(@NotNull PsiType exprType, @NotNull PsiType parameterType, @NotNull PsiElement context);

  public abstract IntentionAction createFix(PsiExpressionList list, int i, PsiType parameterType);
}