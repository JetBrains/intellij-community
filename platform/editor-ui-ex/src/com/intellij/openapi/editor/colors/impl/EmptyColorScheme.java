// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * A base scheme for new schemes (not based on Default/Darcula), imported ones.
 */
@SuppressWarnings("UseJBColor")
public final class EmptyColorScheme extends DefaultColorsScheme {
  public static final String NAME = "Empty";
  public static final EmptyColorScheme INSTANCE = new EmptyColorScheme();

  private static final TextAttributes EMPTY_TEXT = new TextAttributes(Color.BLACK, Color.white, null, EffectType.BOXED, Font.PLAIN);
  private static final TextAttributes DEFAULT_ATTRS = new TextAttributes(Color.GRAY, null, null, EffectType.BOXED, Font.PLAIN);

  private EmptyColorScheme() {
    myAttributesMap.put(HighlighterColors.TEXT.getExternalName(), EMPTY_TEXT);
    initFonts();
  }

  @NotNull
  @Override
  public TextAttributes getAttributes(TextAttributesKey key) {
    TextAttributes attributes = super.getAttributes(key);
    return attributes == null ? DEFAULT_ATTRS : attributes;
  }

  @Nullable
  @Override
  protected TextAttributes getKeyDefaults(@NotNull TextAttributesKey key) {
    return myAttributesMap.get(HighlighterColors.TEXT.getExternalName());
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean hasEditableCopy() {
    return false;
  }
}
