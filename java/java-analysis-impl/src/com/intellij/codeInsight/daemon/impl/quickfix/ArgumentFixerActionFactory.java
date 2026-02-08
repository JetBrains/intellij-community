// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public abstract class ArgumentFixerActionFactory {
  private static final Logger LOG = Logger.getInstance(ArgumentFixerActionFactory.class);

  protected abstract @Nullable PsiExpression getModifiedArgument(PsiExpression expression, final PsiType toType) throws IncorrectOperationException;

  public void registerCastActions(CandidateInfo @NotNull [] candidates, @NotNull PsiCall call, @NotNull Consumer<? super CommonIntentionAction> info) {
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
      // find to which type we can cast this param to get a valid method call
      List<ArgumentCast> actualCasts = new ArrayList<>();
      for (CandidateInfo candidate : methodCandidates) {
        PsiMethod method = (PsiMethod)candidate.getElement();
        PsiSubstitutor substitutor = candidate.getSubstitutor();
        PsiParameter[] parameters = method.getParameterList().getParameters();
        List<ArgumentCast> potentialCasts = new ArrayList<>();
        for (int i = 0; i < expressions.length; i++) {
          PsiExpression expression = expressions[i];
          PsiType exprType = PsiUtil.skipParenthesizedExprDown(expression) instanceof PsiFunctionalExpression fn ? 
                             fn.getFunctionalInterfaceType() : expression.getType();
          PsiType originalParameterType = PsiTypesUtil.getParameterType(parameters, i, true);
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
          potentialCasts.add(new ArgumentCast(i, parameterType));
        }

        if (!potentialCasts.isEmpty()) {
          PsiCall newCall = call;
          for (var argumentCast : potentialCasts) {
            newCall = replaceWithCast(expressions, newCall, argumentCast, newCall == call);
            if (newCall == null) {
              break;
            }
          }
          if (newCall != null) {
            doCheckNewCall(expectedTypeByParent, newCall, () -> {
              for (var argumentCast : potentialCasts) {
                collectCast(actualCasts, suggestedCasts, argumentCast);
              }
              potentialCasts.clear();
            });
          }

          for (var argumentCast : potentialCasts) {
            PsiCall callWithSingleCast = replaceWithCast(expressions, call, argumentCast, true);
            if (callWithSingleCast == null) continue;
            doCheckNewCall(expectedTypeByParent, callWithSingleCast, () -> collectCast(actualCasts, suggestedCasts, argumentCast));
          }
        }
      }
      putCompilableCastsFirst(actualCasts, expressions).forEach(cast -> registerCastIntention(info, list, cast));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void doCheckNewCall(PsiType expectedTypeByParent, PsiCall call, Runnable registerIntentions) {
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    if (resolveResult.getElement() != null && resolveResult.isValidResult()) {
      if (expectedTypeByParent != null && call instanceof PsiCallExpression callExpression) {
        PsiType type = callExpression.getType();
        if (type != null && !TypeConversionUtil.isAssignable(expectedTypeByParent, type)) return;
      }
      registerIntentions.run();
    }
  }

  private void registerCastIntention(@NotNull Consumer<? super CommonIntentionAction> info,
                                     PsiExpressionList list,
                                     ArgumentCast argumentCast) {
    IntentionAction action = createFix(list, argumentCast.argumentIndex(), argumentCast.castType());
    if (action != null) {
      info.accept(action);
    }
  }

  private @Nullable PsiCall replaceWithCast(PsiExpression[] expressions, @NotNull PsiCall origCall, ArgumentCast argumentCast,
                                            boolean shouldCopy) {
    int i = argumentCast.argumentIndex();
    PsiExpression modifiedExpression = getModifiedArgument(expressions[i], argumentCast.castType());
    if (modifiedExpression == null) return null;
    PsiExpressionList argumentList = origCall.getArgumentList();
    if (argumentList == null) return null;
    PsiCall newCall = shouldCopy ? LambdaUtil.copyTopLevelCall(origCall) : origCall;
    if (newCall == null) return null;
    Objects.requireNonNull(newCall.getArgumentList()).getExpressions()[i].replace(modifiedExpression);
    return newCall;
  }

  private static void collectCast(@NotNull List<@NotNull ArgumentCast> actualCasts,
                                  @NotNull Map<@NotNull Integer, @NotNull Set<@NotNull String>> suggestedCasts,
                                  @NotNull ArgumentCast argumentCast) {
    actualCasts.add(argumentCast);
    suggestedCasts.get(argumentCast.argumentIndex()).add(argumentCast.castType().getCanonicalText());
  }

  private List<ArgumentCast> putCompilableCastsFirst(@NotNull List<@NotNull ArgumentCast> casts,
                                                     @NotNull PsiExpression @NotNull [] expressions) {
    List<ArgumentCast> compilableCasts = new ArrayList<>();
    List<ArgumentCast> nonCompilableCasts = new ArrayList<>();
    for (var argumentCast : casts) {
      if (doesFixCauseOtherCompilationErrors(expressions[argumentCast.argumentIndex()], argumentCast.castType())) {
        nonCompilableCasts.add(argumentCast);
      }
      else {
        compilableCasts.add(argumentCast);
      }
    }
    return ContainerUtil.concat(compilableCasts, nonCompilableCasts);
  }

  protected boolean doesFixCauseOtherCompilationErrors(@NotNull PsiExpression expression, @NotNull PsiType parameterType) {
    return false;
  }

  public abstract boolean areTypesConvertible(@NotNull PsiType exprType, @NotNull PsiType parameterType, @NotNull PsiElement context);

  public abstract IntentionAction createFix(PsiExpressionList list, int i, PsiType parameterType);

  /**
   * Cast of an argument in a method call to a type.
   * @param argumentIndex index of the argument that is cast
   * @param castType      type in a cast expression
   */
  private record ArgumentCast(int argumentIndex, @NotNull PsiType castType) {
  }
}