// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class FinalUtils {
  private FinalUtils() {}

  public static boolean canBeFinal(@NotNull PsiVariable variable) {
    if (variable.getInitializer() != null || variable instanceof PsiParameter) {
      // parameters have an implicit initializer
      return !VariableAccessUtils.variableIsAssigned(variable);
    }
    if (variable instanceof PsiField && !HighlightControlFlowUtil.isFieldInitializedAfterObjectConstruction((PsiField)variable)) {
      return false;
    }
    PsiElement scope = variable instanceof PsiField
                       ? PsiUtil.getTopLevelClass(variable)
                       : PsiUtil.getVariableCodeBlock(variable, null);
    if (scope == null) return false;
    Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems = new HashMap<>();
    Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems = new HashMap<>();
    PsiElementProcessor<PsiElement> elementDoesNotViolateFinality = e -> {
      if (!(e instanceof PsiReferenceExpression ref)) return true;
      if (!ref.isReferenceTo(variable)) return true;
      HighlightInfo.Builder highlightInfo = HighlightControlFlowUtil
        .checkVariableInitializedBeforeUsage(ref, variable, uninitializedVarProblems, variable.getContainingFile(), true);
      if (highlightInfo != null) return false;
      if (!PsiUtil.isAccessedForWriting(ref)) return true;
      if (!LocalsOrMyInstanceFieldsControlFlowPolicy.isLocalOrMyInstanceReference(ref)) return false;
      if (ControlFlowUtil.isVariableAssignedInLoop(ref, variable)) return false;
      if (variable instanceof PsiField) {
        if (PsiUtil.findEnclosingConstructorOrInitializer(ref) == null) return false;
        PsiElement innerScope = HighlightControlFlowUtil.getElementVariableReferencedFrom(variable, ref);
        if (innerScope != null && innerScope != ((PsiField)variable).getContainingClass()) return false;
      }
      return HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, ref, finalVarProblems) == null;
    };
    return PsiTreeUtil.processElements(scope, elementDoesNotViolateFinality);
  }
}