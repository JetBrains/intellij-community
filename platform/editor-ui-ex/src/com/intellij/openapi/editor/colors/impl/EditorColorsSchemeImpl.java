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
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class EditorColorsSchemeImpl extends AbstractColorsScheme implements ExternalizableScheme {
  private final Map<TextAttributesKey, TextAttributes> myAttributesTempMap = ContainerUtil.newConcurrentMap();
  
  public EditorColorsSchemeImpl(EditorColorsScheme parentScheme) {
    super(parentScheme);
  }
  
  @Override
  public void copyTo(AbstractColorsScheme newScheme) {
    super.copyTo(newScheme);
    myAttributesTempMap.clear();
  }
  
  @Override
  public void setAttributes(@NotNull TextAttributesKey key, @NotNull TextAttributes attributes) {
    if (TextAttributesKey.isTemp(key)) {
      myAttributesTempMap.put(key, attributes);
    }
    else if (attributes == INHERITED_ATTRS_MARKER || !Comparing.equal(attributes, getAttributes(key))) {
      myAttributesMap.put(key, attributes);
      myAttributesTempMap.clear();
    }
  }

  @Override
  public void setColor(ColorKey key, Color color) {
    if (color == INHERITED_COLOR_MARKER || !Comparing.equal(color, getColor(key))) {
      myColorsMap.put(key, ObjectUtils.notNull(color, NULL_COLOR_MARKER));
    }
  }

  @Override
  public TextAttributes getAttributes(@Nullable TextAttributesKey key) {
    if (key != null) {
      if (TextAttributesKey.isTemp(key)) {
        return myAttributesTempMap.get(key);
      }
      
      TextAttributes attributes = getDirectlyDefinedAttributes(key);
      if (attributes != null && attributes != INHERITED_ATTRS_MARKER) {
        return attributes;
      }

      TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
      if (fallbackKey != null) {
        attributes = getFallbackAttributes(fallbackKey);
        if (attributes != null) {
          return attributes;
        }
      }
    }
    return myParentScheme.getAttributes(key);
  }

  @Nullable
  @Override
  public Color getColor(ColorKey key) {
    if (key != null) {
      Color color = getDirectlyDefinedColor(key);
      if (color == NULL_COLOR_MARKER) {
        return null;
      }
      if (color != null && color != INHERITED_COLOR_MARKER) {
        return color;
      }

      ColorKey fallbackKey = key.getFallbackColorKey();
      if (fallbackKey != null) {
        color = getFallbackColor(fallbackKey);
        if (color != null) {
          return color;
        }
      }
    }
    return myParentScheme.getColor(key);
  }

  @Override
  public Object clone() {
    EditorColorsSchemeImpl newScheme = new EditorColorsSchemeImpl(myParentScheme);
    copyTo(newScheme);
    newScheme.setName(getName());
    newScheme.setDefaultMetaInfo(this);
    return newScheme;
  }

  @Override
  protected boolean attributesEqual(AbstractColorsScheme otherScheme) {
    return compareAttributes(otherScheme, new ArrayList<>());
  }

  @Override
  protected boolean colorsEqual(AbstractColorsScheme otherScheme, @Nullable Predicate<ColorKey> colorKeyFilter) {
    Collection<Predicate<ColorKey>> filters = new ArrayList<>();
    if (colorKeyFilter != null) {
      filters.add(colorKeyFilter);
    }
    return compareColors(otherScheme, filters);
  }

  private boolean compareAttributes(@NotNull AbstractColorsScheme otherScheme,
                                    @NotNull Collection<Function<TextAttributesKey, Boolean>> filters) {
    for (TextAttributesKey key : myAttributesMap.keySet()) {
      if (!isTextAttributeKeyIgnored(filters, key) && !getAttributes(key).equals(otherScheme.getAttributes(key))) {
        return false;
      }
    }
    filters.add(key -> myAttributesMap.containsKey(key));
    if (myParentScheme instanceof EditorColorsSchemeImpl &&
        !((EditorColorsSchemeImpl)myParentScheme).compareAttributes(otherScheme, filters)) {
      return false;
    }
    return true;
  }

  private static boolean isTextAttributeKeyIgnored(@NotNull Collection<Function<TextAttributesKey, Boolean>> filters,
                                                   TextAttributesKey key) {
    for (Function<TextAttributesKey, Boolean> filter : filters) {
      if (filter.apply(key)) return true;
    }
    return false;
  }
  
  private boolean compareColors(@NotNull AbstractColorsScheme otherScheme,
                                @NotNull Collection<Predicate<ColorKey>> filters) {
    for (ColorKey key : myColorsMap.keySet()) {
      Color thisColor = getColor(key);
      Color otherColor = otherScheme.getColor(key);
      if (isColorKeyAccepted(filters, key) && !Comparing.equal(thisColor, otherColor)) {
        return false;
      }
    }
    filters.add(key -> !myColorsMap.containsKey(key));
    if (myParentScheme instanceof EditorColorsSchemeImpl &&
        !((EditorColorsSchemeImpl)myParentScheme).compareColors(otherScheme, filters)) {
      return false;
    }
    return true;
  }

  private static boolean isColorKeyAccepted(@NotNull Collection<Predicate<ColorKey>> filters, @NotNull ColorKey key) {
    for (Predicate<ColorKey> filter : filters) {
      if (!filter.test(key)) return false;
    }
    return true;
  }
}
