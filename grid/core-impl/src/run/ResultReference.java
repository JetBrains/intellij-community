package com.intellij.database.run;

import org.jetbrains.annotations.NotNull;

public class ResultReference {
  private static final String RESULT_REFERENCE = "<result reference>";
  private final Object reference;

  public ResultReference(@NotNull Object reference) {
    this.reference = reference;
  }

  public @NotNull Object getReference() {
    return reference;
  }

  @Override
  public String toString() {
    return RESULT_REFERENCE;
  }
}
