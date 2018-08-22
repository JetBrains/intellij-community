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

import com.intellij.openapi.util.Comparing;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ColorKey implements Comparable<ColorKey> {
  private static final ConcurrentMap<String, ColorKey> ourRegistry = new ConcurrentHashMap<>();

  @NotNull
  private final String myExternalName;
  private final Color myDefaultColor;

  private ColorKey(@NotNull String externalName, Color defaultColor) {
    myExternalName = externalName;
    myDefaultColor = defaultColor;
  }

  @NotNull
  public static ColorKey find(@NotNull String externalName) {
    return ourRegistry.computeIfAbsent(externalName, name -> new ColorKey(name,null));
  }

  @Override
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
    return myDefaultColor;
  }

  @Nullable
  @Deprecated
  public ColorKey getFallbackColorKey() {
    return null;
  }

  @NotNull
  public static ColorKey createColorKey(@NonNls @NotNull String externalName) {
    return find(externalName);
  }

  @NotNull
  public static ColorKey createColorKey(@NonNls @NotNull String externalName, @Nullable Color defaultColor) {
    ColorKey existing = ourRegistry.get(externalName);
    if (existing != null) {
      if (Comparing.equal(existing.getDefaultColor(), defaultColor)) return existing;
      // some crazy life cycle assumes we should overwrite default color
      // (e.g. when read from external schema HintUtil.INFORMATION_COLOR_KEY with null color, then try to re-create it with not-null color in HintUtil initializer)
      ourRegistry.remove(externalName, existing);
    }
    ColorKey newKey = new ColorKey(externalName, defaultColor);
    return ConcurrencyUtil.cacheOrGet(ourRegistry, externalName, newKey);
  }

  @Override
  public int hashCode() {
    return myExternalName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ColorKey && myExternalName.equals(((ColorKey)obj).myExternalName);
  }
}
