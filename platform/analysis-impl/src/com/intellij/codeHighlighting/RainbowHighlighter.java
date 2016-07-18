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
package com.intellij.codeHighlighting;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class RainbowHighlighter {
  private final float[] myFloats;
  @NotNull private final TextAttributesScheme myColorsScheme;

  public RainbowHighlighter(@Nullable TextAttributesScheme colorsScheme) {
    myColorsScheme = colorsScheme != null ? colorsScheme : EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = myColorsScheme.getAttributes(DefaultLanguageHighlighterColors.CONSTANT);
    Color foregroundColor = attributes.getForegroundColor();
    float[] components = foregroundColor.getRGBColorComponents(null);
    myFloats = Color.RGBtoHSB((int)(255 * components[0]), (int)(255 * components[0]), (int)(255 * components[0]), null);
  }

  public static final HighlightInfoType RAINBOW_ELEMENT = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, DefaultLanguageHighlighterColors.CONSTANT);

  public static boolean isRainbowEnabled() {
    return Registry.is("editor.rainbow.identifiers", false);
  }

  @NotNull
  public TextAttributes getAttributes(@NotNull String name, @NotNull TextAttributes origin) {
    final Color fg = calculateForeground(name);
    return TextAttributes.fromFlyweight(origin.getFlyweight().withForeground(fg));
  }

  @NotNull
  protected Color calculateForeground(@NotNull String name) {
    int hash = StringHash.murmur(name, 0);
    final List<String> registryColors = StringUtil.split(Registry.get("rainbow.highlighter.colors").asString(), ",");
    if (!registryColors.isEmpty()) {
      final List<Color> colors = registryColors.stream().map((s -> ColorUtil.fromHex(s.trim()))).collect(Collectors.toList());
      if (!colors.isEmpty()) {
        return colors.get(Math.abs(hash) % colors.size());
      }
    }

    final float colors = 36.0f;
    final float v = Math.round(Math.abs(colors * hash) / Integer.MAX_VALUE) / colors;
    return Color.getHSBColor(v, 0.7f, myFloats[2] + .3f);
  }

  public HighlightInfo getInfo(@Nullable String nameKey, @Nullable PsiElement id, @Nullable TextAttributesKey colorKey) {
    if (id == null || nameKey == null || StringUtil.isEmpty(nameKey)) return null;
    if (colorKey == null) colorKey = DefaultLanguageHighlighterColors.LOCAL_VARIABLE;
    final TextAttributes attributes = getAttributes(nameKey, myColorsScheme.getAttributes(colorKey));
    return HighlightInfo
      .newHighlightInfo(RAINBOW_ELEMENT)
      .textAttributes(attributes)
      .range(id)
      .create();
  }
}
