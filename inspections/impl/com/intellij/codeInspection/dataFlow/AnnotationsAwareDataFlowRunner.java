package com.intellij.codeInspection.dataFlow;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author peter
 */
public class AnnotationsAwareDataFlowRunner extends DataFlowRunner {

  @Override
  protected Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock, InstructionVisitor visitor) {
    final Collection<DfaMemoryState> initialStates = super.createInitialStates(psiBlock, visitor);
    final PsiElement parent = psiBlock.getParent();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;

      //todo move out from generic runner
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        if (AnnotationUtil.isNotNull(parameter)) {
          final DfaVariableValue value = getFactory().getVarFactory().create(parameter, false);
          for (final DfaMemoryState initialState : initialStates) {
            initialState.applyNotNull(value);
          }
        }
      }
    }
    return initialStates;
  }
}
