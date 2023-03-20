// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents linkedOrConsumed field for Stream
 * UNKNOWN-->OPEN
 * \     \
 * ------->CONSUMED
 */
public enum DfStreamStateType implements DfType {
  UNKNOWN, OPEN, CONSUMED;

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other.equals(this) || other == DfType.BOTTOM) return true;
    if (other instanceof DfStreamStateType && this == UNKNOWN) return true;
    return false;
  }

  @Override
  public @NotNull DfType join(@NotNull DfType other) {
    if (other.equals(this) || other == DfType.BOTTOM) return this;
    if (other instanceof DfStreamStateType) return UNKNOWN;
    return DfType.TOP;
  }

  @Override
  public DfType tryJoinExactly(@NotNull DfType other) {
    if (other.isSuperType(this)) return other;
    if (this.isSuperType(other)) return this;
    return null;
  }

  @Override
  public @NotNull DfType meet(@NotNull DfType other) {
    if (other.isSuperType(this)) return this;
    if (this.isSuperType(other)) return other;
    return BOTTOM;
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    DfType afterRelation = switch (relationType) {
      case EQ -> this;
      case NE -> tryNegate();
      default -> TOP;
    };
    return afterRelation == null ? TOP : afterRelation;
  }

  @Nullable
  @Override
  public DfType tryNegate() {
    return switch (this) {
      case UNKNOWN -> null;
      case OPEN -> CONSUMED;
      case CONSUMED -> OPEN;
    };
  }

  @Override
  public DfType correctTypeOnFlush(DfType typeBeforeFlush) {
    if (typeBeforeFlush == CONSUMED) return typeBeforeFlush;
    return this;
  }
}
