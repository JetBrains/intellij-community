// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.dataFlow.jvm;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.memory.DistinctPairSet;
import com.intellij.codeInspection.dataFlow.memory.EqClass;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Memory-state with some JVM specifics
 */
public class JvmDfaMemoryStateImpl extends DfaMemoryStateImpl {

  public JvmDfaMemoryStateImpl(@NotNull DfaValueFactory factory) {
    super(factory);
  }

  protected JvmDfaMemoryStateImpl(DfaMemoryStateImpl toCopy) {
    super(toCopy);
  }

  @Override
  public @NotNull DfaMemoryStateImpl createCopy() {
    return new JvmDfaMemoryStateImpl(this);
  }

  @Override
  protected void doFlush(@NotNull DfaVariableValue var, boolean markFlushed) {
    if (getDfType(var) == DfTypes.NULL) {
      myStack.replaceAll(val -> val == var ? getFactory().fromDfType(DfTypes.NULL) : val);
    }
    super.doFlush(var, markFlushed);
  }

  @Override
  protected boolean meetVariableType(@NotNull DfaVariableValue var,
                                     @NotNull DfType originalType,
                                     @NotNull DfType newType) {
    TypeConstraint newConstraint = TypeConstraint.fromDfType(newType);
    if (newConstraint.isComparedByEquals() && !newConstraint.equals(TypeConstraint.fromDfType(originalType))) {
      // Type is narrowed to java.lang.String, java.lang.Integer, etc.: we consider String & boxed types
      // equivalence by content, but other object types by reference, so we need to remove distinct pairs, if any.
      convertReferenceEqualityToValueEquality(var);
    }
    return super.meetVariableType(var, originalType, newType);
  }

  private void convertReferenceEqualityToValueEquality(DfaValue value) {
    int id = canonicalize(value).getID();
    int index = getRawEqClassIndex(id);

    if (index == -1) return;
    for (Iterator<DistinctPairSet.DistinctPair> iterator = getDistinctClassPairs().iterator(); iterator.hasNext(); ) {
      DistinctPairSet.DistinctPair pair = iterator.next();
      EqClass otherClass = pair.getOtherClass(index);
      if (otherClass != null && getDfType(otherClass.getVariable(0)) != DfTypes.NULL) {
        iterator.remove();
      }
    }
  }

  @Override
  protected void checkEphemeral(DfaValue left, DfaValue right) {
    if (getDfType(right) == DfTypes.NULL) {
      DfaNullability nullability = DfaNullability.fromDfType(getDfType(left));
      if (nullability == DfaNullability.UNKNOWN || nullability == DfaNullability.FLUSHED) {
        markEphemeral();
      }
    }
  }
}
