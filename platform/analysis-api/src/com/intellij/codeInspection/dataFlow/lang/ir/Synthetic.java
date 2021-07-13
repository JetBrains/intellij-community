// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Synthetic implements VariableDescriptor {
  private final int myLocation;
  private final DfType myType;

  Synthetic(int location, DfType type) {
    myLocation = location;
    myType = type;
  }

  @Override
  public @NotNull String toString() {
    return "tmp$" + myLocation;
  }

  @Override
  public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
    return myType;
  }

  @Override
  public boolean isStable() {
    return true;
  }

  public int getLocation() {
    return myLocation;
  }
}
