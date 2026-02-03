// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.colors;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Describes a text attribute key the attributes for which can be configured in a custom
 * colors and fonts page.
 *
 * @see ColorSettingsPage#getAttributeDescriptors()
 */
public final class AttributesDescriptor extends AbstractKeyDescriptor<TextAttributesKey> {
  /** Please use {@link #AttributesDescriptor(Supplier, TextAttributesKey)} instead. */
  public AttributesDescriptor(@NotNull @NlsContexts.AttributeDescriptor String displayName, @NotNull TextAttributesKey key) {
    this(new StaticSupplier(displayName), key);
  }

  /**
   * Creates an attribute descriptor with the specified name and text attributes key.
   *
   * @param displayName the name of the attribute shown in the colors list.
   * @param key         the attributes key for which the colors are specified.
   */
  public AttributesDescriptor(@NotNull Supplier<@NlsContexts.AttributeDescriptor String> displayName, @NotNull TextAttributesKey key) {
    super(displayName, key);
  }

  @SuppressWarnings("RedundantMethodOverride") // binary compatibility
  @Override
  public @NotNull TextAttributesKey getKey() {
    return super.getKey();
  }
}
