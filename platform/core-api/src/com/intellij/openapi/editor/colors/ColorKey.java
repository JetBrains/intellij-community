/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

public final class ColorKey implements Comparable<ColorKey> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.colors.ColorKey");
  private static final Color NULL_COLOR = ColorUtil.marker("NULL_COLOR");

  private static final Map<String, ColorKey> ourRegistry = ConcurrentFactoryMap.createMap(ColorKey::new);

  private final String myExternalName;
  private Color myDefaultColor = NULL_COLOR;
  private ColorKey myFallbackColorKey;

  private ColorKey(@NotNull String externalName) {
    myExternalName = externalName;
  }

  @NotNull
  public static ColorKey find(@NotNull String externalName) {
    return ourRegistry.get(externalName);
  }

  public String toString() {
    return myExternalName;
  }

  @NotNull
  public String getExternalName() {
    return myExternalName;
  }

  @Override
  public int compareTo(@NotNull ColorKey key) {
    return myExternalName.compareTo(key.myExternalName);
  }

  public Color getDefaultColor() {
    if (myDefaultColor == NULL_COLOR) {
      myDefaultColor = null;
    }

    return myDefaultColor;
  }

  @Nullable
  public ColorKey getFallbackColorKey() {
    return myFallbackColorKey;
  }

  public void setFallbackColorKey(@Nullable ColorKey fallbackColorKey) {
    myFallbackColorKey = fallbackColorKey;
    if (fallbackColorKey != null) {
      JBIterable<ColorKey> it = JBIterable.generate(fallbackColorKey, o -> o == this ? null : o.myFallbackColorKey);
      if (it.find(o -> o == this) == this) {
        String cycle = StringUtil.join(it.map(ColorKey::getExternalName), "->");
        LOG.error("Cycle detected: " + cycle);
      }
    }
  }

  @NotNull
  public static ColorKey createColorKey(@NonNls @NotNull String externalName) {
    return find(externalName);
  }

  @NotNull
  public static ColorKey createColorKey(@NonNls @NotNull String externalName, @Nullable ColorKey fallbackColorKey) {
    ColorKey key = createColorKey(externalName);
    key.setFallbackColorKey(fallbackColorKey);
    return key;
  }

  @NotNull
  public static ColorKey createColorKey(@NonNls @NotNull String externalName, @Nullable Color defaultColor) {
    ColorKey key = createColorKey(externalName);

    if (key.getDefaultColor() == null) {
      key.myDefaultColor = defaultColor;
    }
    return key;
  }
}
