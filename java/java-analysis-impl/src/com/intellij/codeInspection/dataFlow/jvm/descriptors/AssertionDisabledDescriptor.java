// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.PsiType;
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

  @NotNull
  @Override
  public PsiType getType(@Nullable DfaVariableValue qualifier) {
    return PsiType.BOOLEAN;
  }

  @Override
  public String toString() {
    return "$assertionsDisabled";
  }

  public static DfaVariableValue getAssertionsDisabledVariable(DfaValueFactory factory) {
    return factory.getVarFactory().createVariableValue(INSTANCE);
  }
}
