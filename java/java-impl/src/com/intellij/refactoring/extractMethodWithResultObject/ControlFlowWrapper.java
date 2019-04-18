// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
class ControlFlowWrapper {
  private final Project myProject;
  private final PsiElement[] myElements;
  private final PsiElement myCodeFragment;
  private final Collection<PsiVariable> myInputVariables = new HashSet<>();

  private ControlFlow myControlFlow;
  private int myFlowStart;
  private int myFlowEnd;

  ControlFlowWrapper(Project project, PsiElement[] elements, PsiElement codeFragment) {
    myProject = project;
    myElements = elements;
    myCodeFragment = codeFragment;
  }

  Collection<PsiVariable> getInputVariables() {
    return myInputVariables;
  }

  boolean prepare() throws PrepareFailedException {
    myControlFlow = createControlFlow();

    myFlowStart = getStartOffset(myControlFlow, myElements);
    if (myFlowStart < 0) {
      return false;
    }
    myFlowEnd = getEndOffset(myControlFlow, myElements);

    List<PsiVariable> inputVariables = ControlFlowUtil.getInputVariables(myControlFlow, myFlowStart, myFlowEnd);
    for (PsiVariable inputVariable : inputVariables) {
      if (inputVariable instanceof PsiLocalVariable) {
        Boolean isAssigned = DefUseUtil.isVariableDefinitelyAssignedAt((PsiLocalVariable)inputVariable, myElements[0]);
        if (isAssigned == null) return false;
        if (!isAssigned) continue;
      }
      myInputVariables.add(inputVariable);
    }
    return true;
  }

  @NotNull
  Set<PsiVariable> getDefinitelyWrittenVariables(@NotNull PsiElement exitElement, boolean useStart) {
    int offset = useStart ? myControlFlow.getStartOffset(exitElement) : myControlFlow.getEndOffset(exitElement);
    if (offset >= 0) {
      PsiVariable[] variables = ControlFlowUtil.getOutputVariables(myControlFlow, myFlowStart, myFlowEnd, new int[]{offset});
      Set<PsiVariable> result = new HashSet<>();
      ContainerUtil.addAll(result, variables);
      return result;
    }
    return Collections.emptySet();
  }

  private static int getEndOffset(ControlFlow controlFlow, PsiElement[] elements) {
    int flowEnd;
    int index = elements.length - 1;
    while (true) {
      flowEnd = controlFlow.getEndOffset(elements[index]);
      if (flowEnd >= 0) break;
      index--;
    }
    return flowEnd;
  }

  private static int getStartOffset(ControlFlow controlFlow, PsiElement[] elements) {
    int flowStart = -1;
    int index = 0;
    while (index < elements.length) {
      flowStart = controlFlow.getStartOffset(elements[index]);
      if (flowStart >= 0) break;
      index++;
    }
    return flowStart;
  }

  @NotNull
  private ControlFlow createControlFlow() throws PrepareFailedException {
    ControlFlow controlFlow;
    try {
      ControlFlowPolicy policy = new LocalsOrInitializedFinalFieldsControlFlowPolicy(myCodeFragment);
      controlFlow = ControlFlowFactory.getInstance(myProject).getControlFlow(myCodeFragment, policy, false, false);
    }
    catch (AnalysisCanceledException e) {
      throw new PrepareFailedException(RefactoringBundle.message("extract.method.control.flow.analysis.failed"), e.getErrorElement());
    }
    return controlFlow;
  }


  static class LocalsOrInitializedFinalFieldsControlFlowPolicy extends LocalsControlFlowPolicy {
    private final PsiClass myContainingClass;

    LocalsOrInitializedFinalFieldsControlFlowPolicy(@NotNull PsiElement codeFragment) {
      super(codeFragment);
      myContainingClass = codeFragment instanceof PsiMethod ? ((PsiMethod)codeFragment).getContainingClass() : null;
    }

    @Override
    public PsiVariable getUsedVariable(@NotNull PsiReferenceExpression refExpr) {
      if (myContainingClass == null) {
        return super.getUsedVariable(refExpr);
      }

      PsiElement qualifier = refExpr.getQualifier();
      if (qualifier != null) {
        if (qualifier instanceof PsiExpression) {
          qualifier = PsiUtil.skipParenthesizedExprDown((PsiExpression)qualifier);
          if (!(qualifier instanceof PsiThisExpression) || ((PsiThisExpression)qualifier).getQualifier() != null) {
            return null;
          }
        }
      }

      PsiElement resolved = refExpr.resolve();
      if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) {
        return super.getUsedVariable(refExpr);
      }
      if (resolved instanceof PsiField) {
        // special case for constructor and initializer
        PsiField field = (PsiField)resolved;
        if (field.getContainingClass() == myContainingClass && field.hasModifierProperty(PsiModifier.FINAL)) {
          PsiElement parent = PsiUtil.skipParenthesizedExprUp(refExpr.getParent());
          if (parent instanceof PsiAssignmentExpression) {
            PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
            if (JavaTokenType.EQ.equals(assignment.getOperationTokenType()) &&
                PsiTreeUtil.isAncestor(assignment.getLExpression(), refExpr, false)) {
              return field;
            }
          }
        }
      }
      return null;
    }
  }
}
