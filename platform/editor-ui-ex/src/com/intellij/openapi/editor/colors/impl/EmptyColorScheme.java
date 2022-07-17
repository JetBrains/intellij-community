// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * A base scheme for new schemes (not based on Default/Darcula), imported ones.
 */
@SuppressWarnings("UseJBColor")
public final class EmptyColorScheme extends DefaultColorsScheme {
  private static final TextAttributes DEFAULT_ATTRS = new TextAttributes(Color.GRAY, null, null, EffectType.BOXED, Font.PLAIN);
  public static final String NAME = "Empty";
  public static final EmptyColorScheme INSTANCE = new EmptyColorScheme();

  private EmptyColorScheme() {
    myAttributesMap.put(HighlighterColors.TEXT.getExternalName(), DEFAULT_ATTRS);
    initFonts();
  }

  @NotNull
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
