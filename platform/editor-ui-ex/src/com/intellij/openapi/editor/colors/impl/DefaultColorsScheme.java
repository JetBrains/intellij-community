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
  public TextAttributes getAttributes(@NotNull TextAttributesKey key, boolean isUseDefault) {
    TextAttributes attrs = myAttributesMap.get(key);
    if (attrs == null) {
      if (key.getFallbackAttributeKey() != null) {
        attrs = getFallbackAttributes(key.getFallbackAttributeKey());
        if (attrs != null && attrs != TextAttributes.USE_INHERITED_MARKER) {
          return attrs;
        }
      }

      if (isUseDefault) {
        attrs = getKeyDefaults(key);
      }
    }
    return attrs;
  }

  @Nullable
  protected TextAttributes getKeyDefaults(@NotNull TextAttributesKey key) {
    return key.getDefaultAttributes();
  }

  @Nullable
  @Override
  public Color getColor(ColorKey key) {
    if (key == null) return null;
    Color color = myColorsMap.get(key);
    return color != null ? color : key.getDefaultColor();
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
