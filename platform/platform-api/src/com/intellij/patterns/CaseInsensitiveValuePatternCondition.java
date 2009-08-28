/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.NotNull;
import com.intellij.util.ProcessingContext;

/**
 * @author peter
*/
public class CaseInsensitiveValuePatternCondition extends PatternCondition<String> {
  private final String[] myValues;

  public CaseInsensitiveValuePatternCondition(String methodName, final String... values) {
    super(methodName);
    myValues = values;
  }

  public String[] getValues() {
    return myValues;
  }

  public boolean accepts(@NotNull final String str, final ProcessingContext context) {
    for (final String value : myValues) {
      if (str.equalsIgnoreCase(value)) return true;
    }
    return false;
  }

}
