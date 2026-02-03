// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import org.jetbrains.annotations.NotNull;

public abstract class JvmVariableDescriptor implements VariableDescriptor {
  @Override
  public boolean alwaysEqualsToItself(@NotNull DfType type) {
    return !type.isSuperType(DfTypes.FLOAT_NAN) &&
           !type.isSuperType(DfTypes.DOUBLE_NAN);
  }
}
