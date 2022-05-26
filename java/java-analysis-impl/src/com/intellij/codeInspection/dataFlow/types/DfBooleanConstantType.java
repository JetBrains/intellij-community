// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import org.jetbrains.annotations.NotNull;

public final class DfBooleanConstantType extends DfConstantType<Boolean> implements DfBooleanType {
  DfBooleanConstantType(boolean value) {
    super(value);
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (other.equals(this)) return this;
    if (other instanceof DfBooleanType) return DfTypes.BOOLEAN;
    return DfType.TOP;
  }

  @Override
  public @NotNull DfType tryJoinExactly(@NotNull DfType other) {
    return join(other);
  }

  @NotNull
  @Override
  public DfType tryNegate() {
    return getValue() ? DfTypes.FALSE : DfTypes.TRUE;
  }
}
