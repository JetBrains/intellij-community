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
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptorWithPath;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class RainbowAttributeDescriptor implements EditorSchemeAttributeDescriptorWithPath {
  private final String myGroup;
  private final String myDisplayName;
  private final EditorColorsScheme myScheme;

  // can be shared between instances
  private final RainbowInSchemeState myCurState;
  private final RainbowInSchemeState myInitState;

  public RainbowAttributeDescriptor(@NotNull String group,
                                    @NotNull String displayNameWithPath,
                                    @NotNull EditorColorsScheme scheme,
                                    RainbowInSchemeState initState,
                                    RainbowInSchemeState curState) {
    myDisplayName = displayNameWithPath;
    myInitState = initState;
    myCurState = curState;
    myScheme = scheme;
    myGroup = group;
  }

  @Override
  public String toString() {
    return myDisplayName;
  }

  @Override
  public String getGroup() {
    return myGroup;
  }

  @Override
  public String getType() {
    return RainbowHighlighter.RAINBOW_TYPE;
  }

  @Override
  public EditorColorsScheme getScheme() {
    return myScheme;
  }

  @Override
  public void apply(@NotNull EditorColorsScheme scheme) {
    myCurState.apply(scheme);
  }

  @Override
  public boolean isModified() {
    return !myInitState.equals(myCurState);
  }

  public List<Pair<Boolean, Color>> getRainbowCurState() {
    return myCurState.myRainbowState;
  }

  public Color getDefaultColor(int index) {
    return RainbowHighlighter.getRainbowKeys().get(index).getDefaultAttributes().getForegroundColor();
  }

  public static class RainbowInSchemeState {
    private final List<Pair<Boolean, Color>> myRainbowState = new ArrayList<>();

    public RainbowInSchemeState(@NotNull EditorColorsScheme scheme) {
      for (TextAttributesKey rainbowKey : RainbowHighlighter.getRainbowKeys()) {
        myRainbowState.add(getColorStateFromScheme(scheme, rainbowKey));
      }
    }

    public RainbowInSchemeState(@NotNull RainbowInSchemeState state) {
      copyFrom(state);
    }

    public void copyFrom(@NotNull RainbowInSchemeState state) {
      assert this != state;

      myRainbowState.clear();
      myRainbowState.addAll(state.myRainbowState);
    }

    public void apply(@NotNull EditorColorsScheme scheme) {
      int i = 0;
      for (TextAttributesKey rainbowKey : RainbowHighlighter.getRainbowKeys()) {
        Pair<Boolean, Color> pair = myRainbowState.get(i);
        scheme.setAttributes(rainbowKey, pair.first ? new TextAttributes(pair.second, null, null, null, Font.PLAIN)
                                                    : rainbowKey.getDefaultAttributes());
        ++i;
      }
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

      RainbowInSchemeState state = (RainbowInSchemeState)o;
      return myRainbowState.equals(state.myRainbowState);
    }

    @Override
    public int hashCode() {
      return myRainbowState.hashCode();
    }
  }
}
