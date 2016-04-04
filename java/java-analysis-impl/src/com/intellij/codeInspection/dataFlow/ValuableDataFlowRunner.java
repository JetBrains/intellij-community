/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiExpression;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Gregory.Shrago
 */
class ValuableDataFlowRunner extends DataFlowRunner {
  @NotNull
  @Override
  protected DfaMemoryState createMemoryState() {
    return new MyDfaMemoryState(getFactory());
  }

  static class MyDfaMemoryState extends DfaMemoryStateImpl {
    private MyDfaMemoryState(@NotNull DfaValueFactory factory) {
      super(factory);
    }

    private MyDfaMemoryState(@NotNull DfaMemoryStateImpl toCopy) {
      super(toCopy);
    }

    @NotNull
    @Override
    public DfaMemoryStateImpl createCopy() {
      return new MyDfaMemoryState(this);
    }

    @NotNull
    @Override
    protected DfaVariableState createVariableState(@NotNull DfaVariableValue var) {
      return new ValuableDfaVariableState(var);
    }

    @Override
    public void flushFields() {
      // this is a compile time values analysis/resolve so do not flush variables
    }
  }

  static class ValuableDfaVariableState extends DfaVariableState {
    private final DfaValue myValue;
    @NotNull final FList<PsiExpression> myConcatenation;

    private ValuableDfaVariableState(@NotNull DfaVariableValue psiVariable) {
      super(psiVariable);
      myValue = null;
      myConcatenation = FList.emptyList();
    }

    private ValuableDfaVariableState(Set<DfaPsiType> instanceofValues,
                                     Set<DfaPsiType> notInstanceofValues,
                                     Nullness nullability, DfaValue value,
                                     @NotNull FList<PsiExpression> concatenation) {
      super(instanceofValues, notInstanceofValues, nullability);
      myValue = value;
      myConcatenation = concatenation;
    }

    @NotNull
    @Override
    protected DfaVariableState createCopy(@NotNull Set<DfaPsiType> instanceofValues, @NotNull Set<DfaPsiType> notInstanceofValues, @NotNull Nullness nullability) {
      return new ValuableDfaVariableState(instanceofValues, notInstanceofValues, nullability, myValue, myConcatenation);
    }

    @NotNull
    @Override
    public DfaVariableState withValue(@Nullable final DfaValue value) {
      if (value == myValue) return this;
      return new ValuableDfaVariableState(myInstanceofValues, myNotInstanceofValues, myNullability, value, myConcatenation);
    }

    ValuableDfaVariableState withExpression(@NotNull final FList<PsiExpression> concatenation) {
      if (concatenation == myConcatenation) return this;
      return new ValuableDfaVariableState(myInstanceofValues, myNotInstanceofValues, myNullability, myValue, concatenation);
    }

    @Override
    public DfaValue getValue() {
      return myValue;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ValuableDfaVariableState)) return false;
      if (!super.equals(o)) return false;

      ValuableDfaVariableState state = (ValuableDfaVariableState)o;

      if (!myConcatenation.equals(state.myConcatenation)) return false;
      if (myValue != null ? !myValue.equals(state.myValue) : state.myValue != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (myValue != null ? myValue.hashCode() : 0);
      result = 31 * result + myConcatenation.hashCode();
      return result;
    }
  }
}
