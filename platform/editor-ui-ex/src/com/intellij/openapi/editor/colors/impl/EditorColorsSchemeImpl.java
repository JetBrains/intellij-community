/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Yura Cangea
 */
public class EditorColorsSchemeImpl extends AbstractColorsScheme implements ExternalizableScheme {
  public EditorColorsSchemeImpl(EditorColorsScheme parentScheme) {
    super(parentScheme);
  }

  @Override
  public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
    if (attributes != getAttributes(key)) {
      myAttributesMap.put(key, attributes);
    }
  }

  @Override
  public void setColor(ColorKey key, Color color) {
    if (!Comparing.equal(color, getColor(key))) {
      myColorsMap.put(key, color);
    }
  }

  @Override
  public TextAttributes getAttributes(TextAttributesKey key) {
    if (key != null) {
      TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
      TextAttributes attributes = getDirectlyDefinedAttributes(key);
      if (fallbackKey == null) {
        if (containsValue(attributes)) return attributes;
      }
      else {
        if (containsValue(attributes) && !attributes.isFallbackEnabled()) return attributes;
        attributes = getFallbackAttributes(fallbackKey);
        if (containsValue(attributes)) return attributes;
      }
    }
    return myParentScheme.getAttributes(key);
  }

  @Nullable
  @Override
  public Color getColor(ColorKey key) {
    if (myColorsMap.containsKey(key)) {
      return myColorsMap.get(key);
    }
    else {
      return myParentScheme.getColor(key);
    }
  }

  @Override
  public Object clone() {
    EditorColorsSchemeImpl newScheme = new EditorColorsSchemeImpl(myParentScheme);
    copyTo(newScheme);
    newScheme.setName(getName());
    return newScheme;
  }
}
