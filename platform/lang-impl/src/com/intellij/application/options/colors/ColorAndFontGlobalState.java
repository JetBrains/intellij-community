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
import org.jetbrains.annotations.NotNull;

public class ColorAndFontGlobalState {
  public boolean isRainbowOn;

  ColorAndFontGlobalState() {
    isRainbowOn = RainbowHighlighter.isRainbowEnabled();
  }

  public ColorAndFontGlobalState(@NotNull ColorAndFontGlobalState state) {
    copyFrom(state);
  }

  public void copyFrom(@NotNull ColorAndFontGlobalState state) {
    assert this != state;
    isRainbowOn = state.isRainbowOn;
  }

  public void apply() {
    //FIXME: we need better place for per-language state storage
    RainbowHighlighter.setRainbowEnabled(isRainbowOn);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ColorAndFontGlobalState state = (ColorAndFontGlobalState)o;
    return isRainbowOn == state.isRainbowOn;
  }

  @Override
  public int hashCode() {
    return isRainbowOn ? 1 : 0;
  }
}
