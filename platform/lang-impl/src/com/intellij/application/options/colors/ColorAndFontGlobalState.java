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
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

public class ColorAndFontGlobalState {
  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  private boolean myIsRainbowOn;
  public boolean isRainbowOn() {
    return myIsRainbowOn;
  }
  public void setRainbowOn(boolean rainbowOn) {
    if (rainbowOn != myIsRainbowOn) {
      myIsRainbowOn = rainbowOn;
    }
  }

  ColorAndFontGlobalState() {
    myIsRainbowOn = RainbowHighlighter.isRainbowEnabled();
  }

  public ColorAndFontGlobalState(@NotNull ColorAndFontGlobalState state) {
    copyFrom(state);
  }

  public void copyFrom(@NotNull ColorAndFontGlobalState state) {
    assert this != state;
    myIsRainbowOn = state.myIsRainbowOn;
  }

  public void apply() {
    //FIXME: we need better place for per-language state storage
    RainbowHighlighter.setRainbowEnabled(myIsRainbowOn);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ColorAndFontGlobalState state = (ColorAndFontGlobalState)o;
    return myIsRainbowOn == state.myIsRainbowOn;
  }

  @Override
  public int hashCode() {
    return myIsRainbowOn ? 1 : 0;
  }

  public void addListener(@NotNull ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void stateChanged() {
    myDispatcher.getMulticaster().settingsChanged();
  }
}
