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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public final class ColorKey implements Comparable<ColorKey> {
  private static final Logger LOG = Logger.getInstance(ColorKey.class);

  public static final Key<Function<ColorKey, Color>> FUNCTION_KEY = Key.create("COLOR_KEY_FUNCTION");
  private static final ConcurrentMap<String, ColorKey> ourRegistry = new ConcurrentHashMap<>();

  private final String myExternalName;
  private final Color myDefaultColor;
  private final ColorKey myFallbackColorKey;

  private ColorKey(@NotNull String externalName, Color defaultColor, ColorKey fallBackColorKey) {
    myExternalName = externalName;
    myDefaultColor = defaultColor;
    myFallbackColorKey = fallBackColorKey;
  }

  @NotNull
  public static ColorKey find(@NotNull String externalName) {
    return ourRegistry.computeIfAbsent(externalName, s -> new ColorKey(s,null,null));
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
  public ColorKey getFallbackColorKey() {
    return myFallbackColorKey;
  }

  @NotNull
  public static ColorKey createColorKey(@NonNls @NotNull String externalName) {
    return find(externalName);
  }

  @NotNull
  public static ColorKey createColorKeyWithFallback(@NonNls @NotNull String externalName, @Nullable ColorKey fallbackColorKey) {
    ColorKey existing = ourRegistry.get(externalName);
    if (existing != null) {
      if (Comparing.equal(existing.getFallbackColorKey(), fallbackColorKey)) return existing;
      // some crazy life cycle assumes we can overwrite color
      ourRegistry.remove(externalName, existing);
    }
    ColorKey newKey = new ColorKey(externalName, existing == null ? null : existing.getDefaultColor(), fallbackColorKey);
    ColorKey res = ConcurrencyUtil.cacheOrGet(ourRegistry, externalName, newKey);

    if (fallbackColorKey != null) {
      JBIterable<ColorKey> it = JBIterable.generate(fallbackColorKey, o -> o == res ? null : o.myFallbackColorKey);
      if (it.find(o -> o == res) == res) {
        String cycle = StringUtil.join(it.map(ColorKey::getExternalName), "->");
        LOG.error("Cycle detected: " + cycle);
      }
    }

    return res;
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
    ColorKey newKey = new ColorKey(externalName, defaultColor, existing == null ? null : existing.getFallbackColorKey());
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
