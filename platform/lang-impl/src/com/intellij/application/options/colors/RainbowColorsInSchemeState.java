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
package com.intellij.application.options.colors;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class RainbowColorsInSchemeState {
  private final RainbowColorsInSchemeState myReferenceState;
  private final List<Pair<Boolean, Color>> myInheritanceAndColors = new ArrayList<>();

  public List<Pair<Boolean, Color>> getInheritanceAndColors() {
    return myInheritanceAndColors;
  }

  private RainbowColorsInSchemeState(@NotNull EditorColorsScheme scheme,
                                     @SuppressWarnings("UnusedParameters") boolean unused) {
    myReferenceState = null;
    for (TextAttributesKey rainbowKey : RainbowHighlighter.RAINBOW_COLOR_KEYS) {
      myInheritanceAndColors.add(getColorStateFromScheme(scheme, rainbowKey));
    }
  }

  public RainbowColorsInSchemeState(@NotNull EditorColorsScheme scheme) {
    myReferenceState = new RainbowColorsInSchemeState(scheme, true);
    copyFrom(myReferenceState);
  }

  private void copyFrom(@NotNull RainbowColorsInSchemeState state) {
    assert this != state;
    myInheritanceAndColors.clear();
    myInheritanceAndColors.addAll(state.myInheritanceAndColors);
  }

  public void apply(@NotNull EditorColorsScheme scheme) {
    int i = 0;
    for (TextAttributesKey rainbowKey : RainbowHighlighter.RAINBOW_COLOR_KEYS) {
      Pair<Boolean, Color> pair = myInheritanceAndColors.get(i);
      scheme.setAttributes(rainbowKey, pair.first ? new TextAttributes(pair.second, null, null, null, Font.PLAIN)
                                                  : rainbowKey.getDefaultAttributes());
      ++i;
    }
    //myReferenceState.copyFrom(this);
  }

  @NotNull
  private static Pair<Boolean, Color> getColorStateFromScheme(@NotNull EditorColorsScheme scheme, TextAttributesKey rainbowKey) {
    TextAttributes schemeAttributes = scheme.getAttributes(rainbowKey);
    @NotNull Color defaultRainbow = rainbowKey.getDefaultAttributes().getForegroundColor();
    Pair<Boolean, Color> pair;
    if (schemeAttributes == null) {
      pair = Pair.create(false, defaultRainbow);
    }
    else {
      Color schemeColor = schemeAttributes.getForegroundColor();
      if (schemeColor == null) {
        pair = Pair.create(false, defaultRainbow);
      }
      else {
        pair = Pair.create(!defaultRainbow.equals(schemeColor), schemeColor);
      }
    }
    return pair;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RainbowColorsInSchemeState state = (RainbowColorsInSchemeState)o;
    return myInheritanceAndColors.equals(state.myInheritanceAndColors);
  }

  @Override
  public int hashCode() {
    return myInheritanceAndColors.hashCode();
  }

  public boolean isModified() {
    return !equals(myReferenceState);
  }
}
