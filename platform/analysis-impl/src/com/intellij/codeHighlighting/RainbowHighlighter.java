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

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringHash;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class RainbowHighlighter {
  private final float[] myFloats;
  private TextAttributes myColorsSchemeAttributes;

  public RainbowHighlighter(@NotNull TextAttributesScheme colorsScheme) {
    myColorsSchemeAttributes = colorsScheme.getAttributes(DefaultLanguageHighlighterColors.CONSTANT);
    float[] components = myColorsSchemeAttributes.getForegroundColor().getRGBColorComponents(null);
    myFloats = Color.RGBtoHSB((int)(255 * components[0]), (int)(255 * components[0]), (int)(255 * components[0]), null);
  }

  public static final HighlightInfoType RAINBOW_ELEMENT = new HighlightInfoType
    .HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, DefaultLanguageHighlighterColors.CONSTANT);

  public static boolean isRainbowEnabled() {
    return Registry.is("editor.rainbow.identifiers", false);
  }

  @NotNull
  public TextAttributes getAttributes(@NotNull String name) {
    int hash = StringHash.murmur(name, 0);
    final float colors = 36.0f;
    final float v = Math.round(Math.abs(colors * hash) / Integer.MAX_VALUE) / colors;
    //System.out.println("name = " + name + " \tv=" + v);
    final Color color = Color.getHSBColor(v, 0.7f, myFloats[2] + .3f);

    final TextAttributes attributes = TextAttributes.fromFlyweight(myColorsSchemeAttributes
                                                                     .getFlyweight()
                                                                     .withForeground(color)
    //fixme: uta: foreground color is not activated for local variables without background color reset
                                                                     .withBackground(UIManager.getColor("EditorPane.background")));

    return attributes;
  }
}
