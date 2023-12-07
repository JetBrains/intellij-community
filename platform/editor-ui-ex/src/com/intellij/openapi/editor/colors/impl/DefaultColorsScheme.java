// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.SchemeState;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class DefaultColorsScheme extends AbstractColorsScheme {
  private String myName;

  public DefaultColorsScheme() {
    super(null);
  }

  @Override
  public @Nullable TextAttributes getAttributes(TextAttributesKey key) {
    return key == null ? null : getAttributes(key, isUseDefaults());
  }

  public @Nullable TextAttributes getAttributes(@NotNull TextAttributesKey key, boolean useDefaults) {
    TextAttributes attrs = attributesMap.get(key.getExternalName());
    if (attrs != null) return attrs;

    TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
    TextAttributes fallback = fallbackKey == null ? null : getFallbackAttributes(fallbackKey);
    if (fallback != null && fallback != AbstractColorsScheme.INHERITED_ATTRS_MARKER) return fallback;

    if (!useDefaults) return null;
    TextAttributes keyDefaults = getKeyDefaults(key);
    if (keyDefaults != null) return keyDefaults;
    return fallbackKey == null ? null : getKeyDefaults(fallbackKey);
  }

  protected @Nullable TextAttributes getKeyDefaults(@NotNull TextAttributesKey key) {
    return key.getDefaultAttributes();
  }

  @Override
  public @Nullable Color getColor(@Nullable ColorKey key) {
    return key == null ? null : getColor(key, isUseDefaults());
  }

  public @Nullable Color getColor(@NotNull ColorKey key, boolean useDefaults) {
    Color color = colorMap.get(key);
    if (color != null) return color == NULL_COLOR_MARKER ? null : color;

    ColorKey fallbackKey = key.getFallbackColorKey();
    Color fallback = fallbackKey == null ? null : getFallbackColor(fallbackKey);
    if (fallback != null && fallback != AbstractColorsScheme.INHERITED_COLOR_MARKER) return fallback;

    if (!useDefaults) return null;
    Color keyDefaults = key.getDefaultColor();
    if (keyDefaults != null) return keyDefaults;
    return fallbackKey == null ? null : fallbackKey.getDefaultColor();
  }

  @Override
  public void readExternal(@NotNull Element parentNode) {
    super.readExternal(parentNode);
    myName = parentNode.getAttributeValue(NAME_ATTR);
  }

  @Override
  public @NotNull String getName() {
    return myName;
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
    newScheme.setName(DEFAULT_SCHEME_NAME);
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

  public @NonNls String getEditableCopyName() {
    return EDITABLE_COPY_PREFIX + myName;
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
