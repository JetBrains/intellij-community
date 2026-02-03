package com.intellij.database.datagrid.mutating;

import org.jetbrains.annotations.Nullable;

public class MutationData {
  private final Object myValue;

  public MutationData(@Nullable Object value) {
    myValue = value;
  }

  public @Nullable Object getValue() {
    return myValue;
  }
}
