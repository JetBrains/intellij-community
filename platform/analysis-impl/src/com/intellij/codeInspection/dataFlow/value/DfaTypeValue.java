// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.types.DfType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class DfaTypeValue extends DfaValue {
  private final @NotNull DfType myType;

  private DfaTypeValue(@NotNull DfaValueFactory factory, @NotNull DfType type) {
    super(factory);
    myType = type;
  }

  @Override
  public @NotNull DfType getDfType() {
    return myType;
  }

  @Override
  public DfaTypeValue bindToFactory(@NotNull DfaValueFactory factory) {
    return factory.fromDfType(myType);
  }

  public static boolean isUnknown(DfaValue value) {
    return value instanceof DfaTypeValue && value.getDfType() == DfType.TOP;
  }

  @Override
  public String toString() {
    return myType.toString();
  }

  /**
   * Checks whether given value is a special value representing method failure, according to its contract
   *
   * @param value value to check
   * @return true if specified value represents method failure
   */
  @Contract("null -> false")
  public static boolean isContractFail(DfaValue value) {
    return value instanceof DfaTypeValue && value.getDfType() == DfType.FAIL;
  }

  static class Factory {
    private final DfaValueFactory myFactory;
    private final Map<DfType, DfaTypeValue> myValues = new HashMap<>();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    @NotNull DfaTypeValue create(@NotNull DfType type) {
      return myValues.computeIfAbsent(type, t -> new DfaTypeValue(myFactory, t));
    }
  }
}
