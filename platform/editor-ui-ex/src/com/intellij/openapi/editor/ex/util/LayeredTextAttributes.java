// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Attributes created as a result of merging a list of {@link TextAttributesKey}s.
 * Used in combination with {@link com.intellij.openapi.fileTypes.SyntaxHighlighter#getTokenHighlights}
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public final class LayeredTextAttributes extends TextAttributes {
  public static @NotNull LayeredTextAttributes create(@NotNull EditorColorsScheme scheme, TextAttributesKey @NotNull [] keys) {
    TextAttributes result = new TextAttributes();

    for (TextAttributesKey key : keys) {
      TextAttributes attributes = scheme.getAttributes(key);
      if (attributes != null) {
        result = TextAttributes.merge(result, attributes);
      }
    }

    return new LayeredTextAttributes(keys, result);
  }

  private final TextAttributesKey[] myKeys;

  private LayeredTextAttributes(TextAttributesKey @NotNull [] keys, @NotNull TextAttributes attributes) {
    super(attributes.getForegroundColor(),
          attributes.getBackgroundColor(),
          attributes.getEffectColor(),
          attributes.getEffectType(),
          attributes.getFontType());
    myKeys = keys;
  }

  public TextAttributesKey @NotNull [] getKeys() {
    return myKeys;
  }
}
