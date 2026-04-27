package com.intellij.database.datagrid.mutating;

import org.jetbrains.annotations.Nullable;

public class MutationData {
  private final Object myValue;
  private final @Nullable Object myMetadata;

  public MutationData(@Nullable Object value) {
    myValue = value;
    myMetadata = null;
  }

  public MutationData(@Nullable Object value, @Nullable Object metadata) {
    myValue = value;
    myMetadata = metadata;
  }

  public @Nullable Object getValue() {
    return myValue;
  }

  public @Nullable Object getMetadata() {
    return myMetadata;
  }
}
