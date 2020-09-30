// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class EditorColorsSchemeImpl extends AbstractColorsScheme implements ExternalizableScheme {
  private final Map<String, TextAttributes> myAttributesTempMap = new ConcurrentHashMap<>();

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
      myAttributesTempMap.put(key.getExternalName(), attributes);
    }
    else if (attributes == INHERITED_ATTRS_MARKER || !Comparing.equal(attributes, getDirectlyDefinedAttributes(key))) {
      myAttributesMap.put(key.getExternalName(), attributes);
      myAttributesTempMap.clear();
    }
  }

  @Override
  public void setColor(ColorKey key, Color color) {
    if (color == INHERITED_COLOR_MARKER || !colorsEqual(color, getDirectlyDefinedColor(key))) {
      myColorsMap.put(key, ObjectUtils.notNull(color, NULL_COLOR_MARKER));
    }
  }

  @Override
  public TextAttributes getAttributes(@Nullable TextAttributesKey key) {
    if (key != null) {
      if (TextAttributesKey.isTemp(key)) {
        return myAttributesTempMap.get(key.getExternalName());
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

  @Override
  public @Nullable Color getColor(ColorKey key) {
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
  protected boolean colorsEqual(AbstractColorsScheme otherScheme, @Nullable Predicate<? super ColorKey> colorKeyFilter) {
    Collection<Predicate<? super ColorKey>> filters = new ArrayList<>();
    if (colorKeyFilter != null) {
      filters.add(colorKeyFilter);
    }
    return compareColors(otherScheme, filters);
  }

  private boolean compareAttributes(@NotNull AbstractColorsScheme otherScheme,
                                    @NotNull Collection<Predicate<? super TextAttributesKey>> filters) {
    for (String keyName : myAttributesMap.keySet()) {
      TextAttributesKey key = TextAttributesKey.find(keyName);
      if (!isTextAttributeKeyIgnored(filters, key) && !getAttributes(key).equals(otherScheme.getAttributes(key))) {
        return false;
      }
    }
    filters.add(key -> myAttributesMap.containsKey(key.getExternalName()));
    if (myParentScheme instanceof EditorColorsSchemeImpl &&
        !((EditorColorsSchemeImpl)myParentScheme).compareAttributes(otherScheme, filters)) {
      return false;
    }
    return true;
  }

  private static boolean isTextAttributeKeyIgnored(@NotNull Collection<? extends Predicate<? super TextAttributesKey>> filters,
                                                   TextAttributesKey key) {
    for (Predicate<? super TextAttributesKey> filter : filters) {
      if (filter.test(key)) return true;
    }
    return false;
  }

  private boolean compareColors(@NotNull AbstractColorsScheme otherScheme,
                                @NotNull Collection<Predicate<? super ColorKey>> filters) {
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

  private static boolean isColorKeyAccepted(@NotNull Collection<? extends Predicate<? super ColorKey>> filters, @NotNull ColorKey key) {
    for (Predicate<? super ColorKey> filter : filters) {
      if (!filter.test(key)) return false;
    }
    return true;
  }
}
