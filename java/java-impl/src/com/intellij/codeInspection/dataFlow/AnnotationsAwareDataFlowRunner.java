/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
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
    if (initialStates == null) {
      return null;
    }

    final PsiElement parent = psiBlock.getParent();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;

      //todo move out from generic runner
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        if (NullableNotNullManager.isNotNull(parameter)) {
          final DfaVariableValue value = getFactory().getVarFactory().createVariableValue(parameter, false);
          for (final DfaMemoryState initialState : initialStates) {
            initialState.applyNotNull(value);
          }
        }
      }
    }
    return initialStates;
  }
}
