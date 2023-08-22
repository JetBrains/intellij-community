// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an edit box to enter a number
 * 
 * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be int or double
 * @param splitLabel label to display around the control
 * @param minValue minimal allowed value of the variable
 * @param maxValue maximal allowed value of the variable
 */
public record OptNumber(@Language("jvm-field-name") @NotNull String bindId, 
                        @NotNull LocMessage splitLabel, int minValue, int maxValue,
                        @Nullable HtmlChunk description) implements OptControl, OptDescribedComponent, OptRegularComponent {
  public OptNumber {
    if (minValue > maxValue) {
      throw new IllegalArgumentException(minValue + ">" + maxValue);
    }
  }
  
  @Override
  public @NotNull OptNumber prefix(@NotNull String bindPrefix) {
    return new OptNumber(bindPrefix + "." + bindId, splitLabel, minValue, maxValue, description);
  }

  /**
   * @param description textual description
   * @return an equivalent number component but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptNumber description(@NotNull @NlsContexts.Tooltip String description) {
    return description(HtmlChunk.text(description));
  }

  /**
   * @param description HTML description
   * @return an equivalent number component but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptNumber description(@NotNull HtmlChunk description) {
    if (this.description != null) {
      throw new IllegalStateException("Description is already set");
    }
    return new OptNumber(bindId, splitLabel, minValue, maxValue, description);
  }
}
