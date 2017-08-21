/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeState;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class DefaultColorsScheme extends AbstractColorsScheme implements ReadOnlyColorsScheme {
  private String myName;

  public DefaultColorsScheme() {
    super(null);
  }

  @Override
  @Nullable
  public TextAttributes getAttributes(TextAttributesKey key) {
    return key == null ? null : getAttributes(key, true);
  }

  @Nullable
  public TextAttributes getAttributes(@NotNull TextAttributesKey key, boolean useDefaults) {
    TextAttributes attrs = myAttributesMap.get(key);
    if (attrs != null) return attrs;

    TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
    TextAttributes fallback = fallbackKey == null ? null : getFallbackAttributes(fallbackKey);
    if (fallback != null && fallback != AbstractColorsScheme.INHERITED_ATTRS_MARKER) return fallback;

    if (!useDefaults) return null;
    TextAttributes keyDefaults = getKeyDefaults(key);
    if (keyDefaults != null) return keyDefaults;
    return fallbackKey == null ? null : getKeyDefaults(fallbackKey);
  }

  @Nullable
  protected TextAttributes getKeyDefaults(@NotNull TextAttributesKey key) {
    return key.getDefaultAttributes();
  }

  @Nullable
  @Override
  public Color getColor(@Nullable ColorKey key) {
    return key == null ? null : getColor(key, true);
  }

  @Nullable
  public Color getColor(@NotNull ColorKey key, boolean useDefaults) {
    Color color = myColorsMap.get(key);
    if (color != null) return color;

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

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes) {
  }

  @Override
  public void setColor(ColorKey key, Color color) {
  }

  @Override
  public void setFont(EditorFontType key, Font font) {
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
  
  public String getEditableCopyName() {
    return SchemeManager.EDITABLE_COPY_PREFIX + myName;
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @NotNull
  @Override
  public SchemeState getSchemeState() {
    return SchemeState.NON_PERSISTENT;
  }
}
