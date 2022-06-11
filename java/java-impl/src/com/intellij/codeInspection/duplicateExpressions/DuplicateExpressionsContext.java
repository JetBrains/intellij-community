// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author Pavel.Dolgov
 */
final class DuplicateExpressionsContext {
  private static final Key<Map<PsiCodeBlock, DuplicateExpressionsContext>> CONTEXTS_KEY = Key.create("DuplicateExpressionsContext");

  private final Map<PsiExpression, List<PsiExpression>> myOccurrences = CollectionFactory.createCustomHashingStrategyMap(new ExpressionHashingStrategy());
  private final ComplexityCalculator myComplexityCalculator = new ComplexityCalculator();
  private final SideEffectCalculator mySideEffectCalculator = new SideEffectCalculator();
  private final CanonicalExpressionProvider myCanonicalExpressionProvider = new CanonicalExpressionProvider();

  void addOccurrence(PsiExpression expression) {
    PsiExpression canonicalExpression = myCanonicalExpressionProvider.getCanonicalExpression(expression);
    List<PsiExpression> list = myOccurrences.computeIfAbsent(canonicalExpression, unused -> new ArrayList<>());
    list.add(expression);
  }

  void forEach(BiConsumer<? super PsiExpression, ? super List<PsiExpression>> consumer) {
    myOccurrences.forEach(consumer);
  }

  int getComplexity(PsiExpression expression) {
    return myComplexityCalculator.getComplexity(expression);
  }

  boolean mayHaveSideEffect(PsiExpression expression) {
    return mySideEffectCalculator.mayHaveSideEffect(expression);
  }

  @Nullable
  static DuplicateExpressionsContext getOrCreateContext(@NotNull PsiExpression expression, @NotNull UserDataHolder session) {
    PsiCodeBlock nearestBody = findNearestBody(expression);
    if (nearestBody != null) {
      Map<PsiCodeBlock, DuplicateExpressionsContext> contexts = session.getUserData(CONTEXTS_KEY);
      if (contexts == null) {
        session.putUserData(CONTEXTS_KEY, contexts = new HashMap<>());
      }
      return contexts.computeIfAbsent(nearestBody, unused -> new DuplicateExpressionsContext());
    }
    return null;
  }

  @Nullable
  static DuplicateExpressionsContext getContext(@Nullable PsiCodeBlock body, @NotNull UserDataHolder session) {
    Map<PsiCodeBlock, DuplicateExpressionsContext> contexts = session.getUserData(CONTEXTS_KEY);
    return contexts != null ? contexts.get(body) : null;
  }

  static PsiCodeBlock findNearestBody(@NotNull PsiExpression expression) {
    return ObjectUtils.tryCast(ControlFlowUtil.findCodeFragment(expression), PsiCodeBlock.class);
  }
}
