// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents editable sorted list of unique strings
 *
 * @param bindId identifier of binding variable used by an option controller; the corresponding variable is expected to be a mutable {@code List<String>}.
 * @param label label above the control
 * @param validator optional validator for content; can validate max-length or be something more complicated
 *                  (e.g., validate that a string is a class-name which is a subclass of specific class)
 */
public record OptStringList(@Language("jvm-field-name") @NotNull String bindId,
                            @NotNull LocMessage label, @Nullable StringValidator validator,
                            @Nullable HtmlChunk description) implements OptControl, OptDescribedComponent, OptRegularComponent {
  @SuppressWarnings("InjectedReferences")
  @Override
  public @NotNull OptStringList prefix(@NotNull String bindPrefix) {
    return new OptStringList(bindPrefix + "." + bindId, label, validator, description);
  }

  /**
   * @param description textual description
   * @return an equivalent string list but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public @NotNull OptStringList description(@NotNull @NlsContexts.Tooltip String description) {
    return description(HtmlChunk.text(description));
  }

  /**
   * @param description HTML description
   * @return an equivalent string list but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public @NotNull OptStringList description(@NotNull HtmlChunk description) {
    if (this.description != null) {
      throw new IllegalStateException("Description is already set");
    }
    return new OptStringList(bindId, label, validator, description);
  }
}
