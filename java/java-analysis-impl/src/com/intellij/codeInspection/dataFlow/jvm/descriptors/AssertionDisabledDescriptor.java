// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A descriptor that represents an assertion status for the analysis
 */
public final class AssertionDisabledDescriptor implements VariableDescriptor {
  private static final AssertionDisabledDescriptor INSTANCE = new AssertionDisabledDescriptor();

  private AssertionDisabledDescriptor() {}

  @Override
  public boolean isStable() {
    return true;
  }

  @Override
  public boolean isImplicitReadPossible() {
    // Variable may be used from CommonDataflow
    return true;
  }

  @Override
  public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
    return DfTypes.BOOLEAN;
  }

  @Override
  public String toString() {
    return "$assertionsDisabled";
  }

  /**
   * @param factory factory to use
   * @return variable that represents assertions status
   */
  public static @NotNull DfaVariableValue createAssertionsDisabledVar(DfaValueFactory factory) {
    return factory.getVarFactory().createVariableValue(INSTANCE);
  }

  /**
   * @param factory factory to use
   * @return variable that represents assertions status; null if it isn't created yet
   */
  public static @Nullable DfaVariableValue getAssertionsDisabledVar(DfaValueFactory factory) {
    return factory.getVarFactory().getVariableValue(INSTANCE, null);
  }
}
