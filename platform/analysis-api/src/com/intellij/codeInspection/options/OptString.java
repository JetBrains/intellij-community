// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an edit box to enter a string
 *
 * @param bindId      identifier of binding variable used by inspection; the corresponding variable is expected to be string
 * @param splitLabel  label to display around the control
 * @param validator   optional validator for content; can validate max-length or be something more complicated
 *                    (e.g., validate that a string is a class-name which is a subclass of specific class)
 * @param width       width of the control in approximate number of characters; if -1 then it will be determined automatically
 */
public record OptString(@Language("jvm-field-name") @NotNull String bindId, @NotNull LocMessage splitLabel,
                        @Nullable StringValidator validator, int width, HtmlChunk description) 
  implements OptControl, OptDescribedComponent, OptRegularComponent {

  /**
   * @param description textual description
   * @return an equivalent edit box but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptString description(@NotNull @NlsContexts.Tooltip String description) {
    return description(HtmlChunk.text(description));
  }

  /**
   * @param description HTML description
   * @return an equivalent edit box but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptString description(@NotNull HtmlChunk description) {
    if (this.description != null) {
      throw new IllegalStateException("Description is already set");
    }
    return new OptString(bindId, splitLabel, validator, width, description);
  }
  
  @Override
  public @NotNull OptString prefix(@NotNull String bindPrefix) {
    return new OptString(bindPrefix + "." + bindId, splitLabel, validator, width, description);
  }
}
