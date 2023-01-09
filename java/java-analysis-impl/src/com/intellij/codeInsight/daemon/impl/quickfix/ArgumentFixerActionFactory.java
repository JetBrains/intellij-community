// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public abstract class ArgumentFixerActionFactory {
  private static final Logger LOG = Logger.getInstance(ArgumentFixerActionFactory.class);

  @Nullable
  protected abstract PsiExpression getModifiedArgument(PsiExpression expression, final PsiType toType) throws IncorrectOperationException;

  public void registerCastActions(CandidateInfo @NotNull [] candidates, @NotNull PsiCall call, @NotNull HighlightInfo.Builder highlightInfo, final TextRange fixRange) {
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
      Map<Integer, Set<String>> suggestedCasts = new HashMap<>();
      // find to which type we can cast this param to get valid method call
      for (CandidateInfo candidate : methodCandidates) {
        PsiMethod method = (PsiMethod)candidate.getElement();
        PsiSubstitutor substitutor = candidate.getSubstitutor();
        Map<Integer, PsiType> potentialCasts = new HashMap<>();
        for (int i = 0; i < expressions.length; i++) {
          PsiExpression expression = expressions[i];
          PsiType exprType = expression.getType();
          PsiType originalParameterType = PsiTypesUtil.getParameterType(method.getParameterList().getParameters(), i, true);
          PsiType parameterType = substitutor.substitute(originalParameterType);
          if (!PsiTypesUtil.isDenotableType(parameterType, call)) continue;
          if (suggestedCasts.computeIfAbsent(i, __ -> new HashSet<>()).contains(parameterType.getCanonicalText())) continue;
          if (TypeConversionUtil.isPrimitiveAndNotNull(exprType) && parameterType instanceof PsiClassType) {
            PsiType unboxedParameterType = PsiPrimitiveType.getUnboxedType(parameterType);
            if (unboxedParameterType != null) {
              parameterType = unboxedParameterType;
            }
          }
          // strict compare since even widening cast may help
          if (Comparing.equal(exprType, parameterType)) continue;
          potentialCasts.put(i, parameterType);
        }

        if (!potentialCasts.isEmpty()) {
          PsiCall newCall = LambdaUtil.copyTopLevelCall(call);
          if (newCall == null) continue;
          if (!ContainerUtil.exists(potentialCasts.entrySet(), entry -> replaceWithCast(expressions, newCall, entry))) {
            doCheckNewCall(expectedTypeByParent, newCall, () -> {
              for (Iterator<Map.Entry<Integer, PsiType>> iterator = potentialCasts.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<Integer, PsiType> entry = iterator.next();
                registerCastIntention(highlightInfo, fixRange, list, suggestedCasts, entry);
                iterator.remove();
              }
            });
          }
          
          for (Map.Entry<Integer, PsiType> entry : potentialCasts.entrySet()) {
            PsiCall callWithSingleCast = LambdaUtil.copyTopLevelCall(call);
            if (callWithSingleCast == null || replaceWithCast(expressions, callWithSingleCast, entry)) continue;
            doCheckNewCall(expectedTypeByParent, callWithSingleCast, () -> registerCastIntention(highlightInfo, fixRange, list, suggestedCasts, entry));
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void doCheckNewCall(PsiType expectedTypeByParent, PsiCall callWithSingleCast, Runnable registerIntentions) {
    JavaResolveResult resolveResult = callWithSingleCast.resolveMethodGenerics();
    if (resolveResult.getElement() != null && resolveResult.isValidResult()) {
      if (expectedTypeByParent != null && callWithSingleCast instanceof PsiCallExpression) {
        PsiType type = ((PsiCallExpression)callWithSingleCast).getType();
        if (type != null && !TypeConversionUtil.isAssignable(expectedTypeByParent, type)) return;
      }
      registerIntentions.run();
    }
  }

  private void registerCastIntention(@NotNull HighlightInfo.Builder builder,
                                     TextRange fixRange,
                                     PsiExpressionList list,
                                     Map<Integer, Set<String>> suggestedCasts,
                                     Map.Entry<Integer, PsiType> entry) {
    suggestedCasts.get(entry.getKey()).add(entry.getValue().getCanonicalText());
    IntentionAction action = createFix(list, entry.getKey(), entry.getValue());
    if (action != null) {
      builder.registerFix(action, null, null, fixRange, null);
    }
  }

  private boolean replaceWithCast(PsiExpression[] expressions, PsiCall newCall, Map.Entry<Integer, PsiType> entry) {
    Integer i = entry.getKey();
    PsiType parameterType = entry.getValue();
    PsiExpression modifiedExpression = getModifiedArgument(expressions[i], parameterType);
    if (modifiedExpression == null) return true;
    PsiExpressionList argumentList = newCall.getArgumentList();
    if (argumentList == null) return true;
    argumentList.getExpressions()[i].replace(modifiedExpression);
    return false;
  }

  public abstract boolean areTypesConvertible(@NotNull PsiType exprType, @NotNull PsiType parameterType, @NotNull PsiElement context);

  public abstract IntentionAction createFix(PsiExpressionList list, int i, PsiType parameterType);
}