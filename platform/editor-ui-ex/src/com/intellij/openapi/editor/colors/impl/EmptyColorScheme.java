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
public class EmptyColorScheme extends DefaultColorsScheme {
  public static final String NAME = "Empty";
  public static final EmptyColorScheme INSTANCE = new EmptyColorScheme();

  private static final TextAttributes EMPTY_TEXT = new TextAttributes(Color.BLACK, Color.white, null, EffectType.BOXED, Font.PLAIN);
  private static final TextAttributes DEFAULT_ATTRS = new TextAttributes(Color.GRAY, null, null, EffectType.BOXED, Font.PLAIN);

  private EmptyColorScheme() {
    myAttributesMap.put(HighlighterColors.TEXT, EMPTY_TEXT);
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
    return myAttributesMap.get(HighlighterColors.TEXT);
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
