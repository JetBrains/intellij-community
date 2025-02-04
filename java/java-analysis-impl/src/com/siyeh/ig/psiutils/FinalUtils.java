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
    return checkIfElementViolatesFinality(variable);
  }

  private static boolean checkIfElementViolatesFinality(@NotNull PsiVariable variable) {
    PsiElement scope = variable instanceof PsiField
                       ? PsiUtil.getTopLevelClass(variable)
                       : PsiUtil.getVariableCodeBlock(variable, null);
    if (scope == null) return false;
    Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems = new HashMap<>();
    Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems = new HashMap<>();
    PsiElementProcessor<PsiElement> elementDoesNotViolateFinality = e -> {
      return checkElementDoesNotViolateFinality(e, variable, uninitializedVarProblems, finalVarProblems);
    };
    return PsiTreeUtil.processElements(scope, elementDoesNotViolateFinality);
  }

  /**
   * Checks whether a given PsiElement causes the PsiVariable to violate finality conditions
   *
   * @param e expression where "variable" may be defined
   * @param variable variable we are checking for finality
   * @param uninitializedVarProblems maps variables to places where they were potentially used before initialization
   * @param finalVarProblems maps variables to places where they have already been defined
   * @return true if element does not violate finality
   */
  public static boolean checkElementDoesNotViolateFinality(PsiElement e,
                                                            PsiVariable variable,
                                                            Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
                                                            Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
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
      PsiElement innerScope = ControlFlowUtil.getScopeEnforcingEffectiveFinality(variable, ref);
      if (innerScope != null && innerScope != ((PsiField)variable).getContainingClass()) return false;
    }
    HighlightInfo.Builder random =
      HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, ref, finalVarProblems);
    return random == null;
  }
}