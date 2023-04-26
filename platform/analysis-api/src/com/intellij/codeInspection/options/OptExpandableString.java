// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an edit box to enter a string, which can be expanded to a multi-line text area. In this case, original string
 * will be split using the specified separator
 *
 * @param bindId    identifier of binding variable used by inspection; the corresponding variable is expected to be string
 * @param label     label to display around the control
 * @param separator separator to split the original string
 */
public record OptExpandableString(@Language("jvm-field-name") @NotNull String bindId, @NotNull LocMessage label,
                                  @NotNull String separator, @Nullable HtmlChunk description)
  implements OptControl, OptDescribedComponent, OptRegularComponent {

  /**
   * @param description textual description
   * @return an equivalent edit box but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptExpandableString description(@NotNull @NlsContexts.Tooltip String description) {
    return description(HtmlChunk.text(description));
  }

  /**
   * @param description HTML description
   * @return an equivalent edit box but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptExpandableString description(@NotNull HtmlChunk description) {
    if (this.description != null) {
      throw new IllegalStateException("Description is already set");
    }
    return new OptExpandableString(bindId, label, separator, description);
  }

  @Override
  public @NotNull OptExpandableString prefix(@NotNull String bindPrefix) {
    return new OptExpandableString(bindPrefix + "." + bindId, label, separator, description);
  }
}
