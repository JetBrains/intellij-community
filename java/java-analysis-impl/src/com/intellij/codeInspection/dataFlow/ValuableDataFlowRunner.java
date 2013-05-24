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

import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiExpression;

/**
 * @author Gregory.Shrago
 */
public class ValuableDataFlowRunner extends AnnotationsAwareDataFlowRunner {

  @Override
  protected DfaMemoryState createMemoryState() {
    return new MyDfaMemoryState(getFactory());
  }

  static class MyDfaMemoryState extends DfaMemoryStateImpl {
    private MyDfaMemoryState(final DfaValueFactory factory) {
      super(factory);
    }

    @Override
    protected DfaMemoryStateImpl createNew() {
      return new MyDfaMemoryState(getFactory());
    }

    @Override
    protected DfaVariableState createVariableState(DfaVariableValue var) {
      return new ValuableDfaVariableState(var);
    }

  }

  static class ValuableDfaVariableState extends DfaVariableState {
    DfaValue myValue;
    PsiExpression myExpression;

    private ValuableDfaVariableState(final DfaVariableValue psiVariable) {
      super(psiVariable);
    }

    protected ValuableDfaVariableState(final ValuableDfaVariableState state) {
      super(state);
      myExpression = state.myExpression;
    }

    @Override
    public void setValue(final DfaValue value) {
      myValue = value;
    }

    @Override
    public DfaValue getValue() {
      return myValue;
    }

    @Override
    protected ValuableDfaVariableState clone() {
      return new ValuableDfaVariableState(this);
    }
  }
}
