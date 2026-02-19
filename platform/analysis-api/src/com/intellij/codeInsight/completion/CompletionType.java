package com.intellij.codeInsight.completion;

public enum CompletionType {
  BASIC,
  SMART,

  /** @deprecated Use CompletionType.BASIC instead */
  @Deprecated CLASS_NAME
}
