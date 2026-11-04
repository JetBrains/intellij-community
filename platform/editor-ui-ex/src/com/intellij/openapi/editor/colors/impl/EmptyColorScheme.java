// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Font;

/**
 * A base scheme for new schemes (not based on Default/Darcula), imported ones.
 */
@SuppressWarnings("UseJBColor")
public final class EmptyColorScheme extends DefaultColorsScheme {
  private static final TextAttributes DEFAULT_ATTRS = new TextAttributes(Color.GRAY, null, null, EffectType.BOXED, Font.PLAIN);
  private static final EmptyColorScheme INSTANCE = new EmptyColorScheme();

  public static @NotNull String getSchemeName() {
    return "Empty";
  }

  public static @NotNull EmptyColorScheme getEmptyScheme() {
    return INSTANCE;
  }

  private EmptyColorScheme() {
    updateSnapshot(current -> current.withAttributes(HighlighterColors.TEXT.getExternalName(), DEFAULT_ATTRS));
  }

  @Override
  protected @NotNull TextAttributes getKeyDefaults(@NotNull TextAttributesKey key) {
    TextAttributes attributes = getSnapshot().getAttributes(HighlighterColors.TEXT.getExternalName());
    return attributes != null ? attributes : DEFAULT_ATTRS;
  }

  @Override
  public @NotNull String getName() {
    return getSchemeName();
  }

  @Override
  public boolean hasEditableCopy() {
    return false;
  }
}
