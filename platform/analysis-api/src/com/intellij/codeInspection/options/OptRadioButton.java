// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a single radio button. Unlike other controls, it's possible to register several radio buttons with the same bindId.
 * They act like a group, setting one option to different values. The semantic is similar to {@link OptDropdown},
 * but the presentation differs. Radio buttons with the same bindId must have different keys.
 *
 * @param bindId identifier of binding variable; the corresponding variable is expected to be a string, boolean, or enum
 * @param label label to display next to a radio button
 * @param key key to assign to a variable. Can be one of following:
 *            Enum constant name, if binding variable is enum;
 *            Boolean constant name ("true" or "false"), if binding variable is boolean;
 *            Unique string, if binding variable is string
 * @param children optional list of children controls to display next to radio button. They are disabled if radio button is unselected
 * @param description if specified, an additional description of the item (may contain simple HTML formatting only,
 *                    no external images, etc.)
 */
public record OptRadioButton(@Language("jvm-field-name") @NotNull String bindId,
                             @NotNull LocMessage label,
                             @NotNull @NonNls String key,
                             @NotNull List<@NotNull OptRegularComponent> children,
                             @Nullable HtmlChunk description) implements OptControl, OptDescribedComponent, OptRegularComponent {
  /**
   * @param value current value of the binding variable
   * @return true if this radio button is selected when the binding variable has the specified value
   */
  public boolean isSelectedFor(@Nullable Object value) {
    if (value == null) return false;
    return key.equals(value instanceof Enum<?> e ? e.name() : value.toString());
  }

  /**
   * @param description textual description
   * @return an equivalent radio button but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public @NotNull OptRadioButton description(@NotNull @NlsContexts.Tooltip String description) {
    return description(HtmlChunk.text(description));
  }

  /**
   * @param description HTML description
   * @return an equivalent radio button but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public @NotNull OptRadioButton description(@NotNull HtmlChunk description) {
    if (this.description != null) {
      throw new IllegalStateException("Description is already set");
    }
    return new OptRadioButton(bindId(), label(), key(), children(), description);
  }

  @Override
  public @NotNull OptRadioButton prefix(@NotNull String bindPrefix) {
    return new OptRadioButton(bindPrefix + "." + bindId, label, key, ContainerUtil.map(children, c -> c.prefix(bindPrefix)), description);
  }
}
