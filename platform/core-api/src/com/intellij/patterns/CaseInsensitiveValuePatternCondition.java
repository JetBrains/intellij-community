// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class CaseInsensitiveValuePatternCondition extends PatternCondition<String> {
  private final String[] myValues;

  public CaseInsensitiveValuePatternCondition(String methodName, final String... values) {
    super(methodName);
    myValues = values;
  }

  public String[] getValues() {
    return myValues;
  }

  @Override
  public boolean accepts(final @NotNull String str, final ProcessingContext context) {
    for (final String value : myValues) {
      if (str.equalsIgnoreCase(value)) return true;
    }
    return false;
  }

}
