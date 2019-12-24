/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Gregory.Shrago
 */
class ValuableDataFlowRunner extends DataFlowRunner {
  ValuableDataFlowRunner(Project project) {
    super(project);
  }

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

    private ValuableDfaVariableState(DfaValue value,
                                     @NotNull FList<PsiExpression> concatenation,
                                     @NotNull DfType dfType) {
      super(dfType);
      myValue = value;
      myConcatenation = concatenation;
    }

    @NotNull
    @Override
    protected DfaVariableState createCopy(@NotNull DfType dfType) {
      return dfType.equals(myDfType) ? this : new ValuableDfaVariableState(myValue, myConcatenation, dfType);
    }

    @NotNull
    @Override
    public DfaVariableState withValue(@Nullable final DfaValue value) {
      if (value == myValue) return this;
      return new ValuableDfaVariableState(value, myConcatenation, myDfType);
    }

    ValuableDfaVariableState withExpression(@NotNull final FList<PsiExpression> concatenation) {
      if (concatenation == myConcatenation) return this;
      return new ValuableDfaVariableState(myValue, concatenation, myDfType);
    }

    @Override
    public boolean isSuperStateOf(DfaVariableState other) {
      return other instanceof ValuableDfaVariableState && myConcatenation.equals(((ValuableDfaVariableState)other).myConcatenation) && 
             Objects.equals(myValue, ((ValuableDfaVariableState)other).myValue) && super.isSuperStateOf(other);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ValuableDfaVariableState) || !super.equals(o)) return false;

      ValuableDfaVariableState state = (ValuableDfaVariableState)o;

      return myConcatenation.equals(state.myConcatenation) && Objects.equals(myValue, state.myValue);
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
