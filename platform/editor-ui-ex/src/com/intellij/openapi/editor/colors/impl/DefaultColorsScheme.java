// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.SchemeState;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

public class DefaultColorsScheme extends AbstractColorsScheme {
  public DefaultColorsScheme() {
    super(null);
  }

  @Override
  public @Nullable TextAttributes getAttributes(@Nullable TextAttributesKey key) {
    return key == null ? null : getAttributes(key, true);
  }

  @Override
  public @Nullable TextAttributes getAttributes(@Nullable TextAttributesKey key, boolean useDefaults) {
    if (key == null) {
      return null;
    }
    TextAttributes attrs = getSnapshot().getAttributes(key.getExternalName());
    if (attrs != null) {
      return attrs;
    }
    TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
    TextAttributes fallback = fallbackKey == null ? null : getFallbackAttributes(fallbackKey);
    if (fallback != null && fallback != INHERITED_ATTRS_MARKER) {
      return fallback;
    }
    if (!useDefaults) {
      return null;
    }
    TextAttributes keyDefaults = getKeyDefaults(key);
    if (keyDefaults != null) {
      return keyDefaults;
    }
    return fallbackKey == null ? null : getKeyDefaults(fallbackKey);
  }

  protected @Nullable TextAttributes getKeyDefaults(@NotNull TextAttributesKey key) {
    return key.getDefaultAttributes();
  }

  @Override
  public @Nullable Color getColor(@Nullable ColorKey key) {
    return key == null ? null : getColor(key, true);
  }

  public @Nullable Color getColor(@NotNull ColorKey key, boolean useDefaults) {
    Color color = getSnapshot().getColor(key);
    if (color != null) {
      return color == NULL_COLOR_MARKER ? null : color;
    }
    ColorKey fallbackKey = key.getFallbackColorKey();
    Color fallback = fallbackKey == null ? null : getFallbackColor(fallbackKey);
    if (fallback != null && fallback != INHERITED_COLOR_MARKER) {
      return fallback;
    }
    if (!useDefaults) {
      return null;
    }
    Color keyDefaults = key.getDefaultColor();
    if (keyDefaults != null) {
      return keyDefaults;
    }
    return fallbackKey == null ? null : fallbackKey.getDefaultColor();
  }

  @Override
  public void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes) {
  }

  @Override
  public void setColor(ColorKey key, Color color) {
  }

  @Override
  public Object clone() {
    EditorColorsSchemeImpl newScheme = new EditorColorsSchemeImpl(this);
    copyTo(newScheme);
    newScheme.setName(EditorColorsScheme.getDefaultSchemeName());
    newScheme.setDefaultMetaInfo(this);
    return newScheme;
  }

  /**
   * Tells if there is an editable user copy of the scheme to be edited.
   * 
   * @return True if the editable copy shall exist, false if the scheme is non-editable.
   */
  public boolean hasEditableCopy() {
    return true;
  }

  public @NonNls @NotNull String getEditableCopyName() {
    return EDITABLE_COPY_PREFIX + getName();
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public @NotNull SchemeState getSchemeState() {
    return SchemeState.NON_PERSISTENT;
  }
}
