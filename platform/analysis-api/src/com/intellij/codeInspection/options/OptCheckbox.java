// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a two-state check-box (checked or unchecked)
 *
 * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be boolean
 * @param label label to display next to a checkbox
 * @param children optional list of children controls to display next to checkbox. They are disabled if checkbox is unchecked
 * @param description if specified, an additional description of the item (may contain simple HTML formatting only, 
 *                    no external images, etc.)
 */
public record OptCheckbox(@Language("jvm-field-name") @NotNull String bindId,
                          @NotNull LocMessage label,
                          @NotNull List<@NotNull OptRegularComponent> children,
                          @Nullable HtmlChunk description) implements OptControl, OptDescribedComponent, OptRegularComponent {
  /**
   * @param description textual description
   * @return an equivalent checkbox but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptCheckbox description(@NotNull @NlsContexts.Tooltip String description) {
    return description(HtmlChunk.text(description));
  }

  /**
   * @param description HTML description
   * @return an equivalent checkbox but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptCheckbox description(@NotNull HtmlChunk description) {
    if (this.description != null) {
      throw new IllegalStateException("Description is already set");
    }
    return new OptCheckbox(bindId(), label(), children(), description);
  }

  @Override
  public @NotNull OptCheckbox prefix(@NotNull String bindPrefix) {
    return new OptCheckbox(bindPrefix + "." + bindId, label, ContainerUtil.map(children, c -> c.prefix(bindPrefix)), description);
  }
}
