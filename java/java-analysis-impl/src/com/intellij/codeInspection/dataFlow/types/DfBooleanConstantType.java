// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import org.jetbrains.annotations.NotNull;

public final class DfBooleanConstantType extends DfConstantType<Boolean> implements DfBooleanType {
  DfBooleanConstantType(boolean value) {
    super(value);
  }

  @Override
  public @NotNull DfType join(@NotNull DfType other) {
    if (other.equals(this)) return this;
    if (other instanceof DfBooleanType) return DfTypes.BOOLEAN;
    return DfType.TOP;
  }

  @Override
  public @NotNull DfType tryJoinExactly(@NotNull DfType other) {
    return join(other);
  }

  @Override
  public @NotNull DfType tryNegate() {
    return getValue() ? DfTypes.FALSE : DfTypes.TRUE;
  }
}
