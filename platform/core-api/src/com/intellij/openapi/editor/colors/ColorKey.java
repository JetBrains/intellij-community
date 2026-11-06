// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public final class ColorKey implements Comparable<ColorKey> {
  private static final Logger LOG = Logger.getInstance(ColorKey.class);
  private static final ConcurrentMap<String, ColorKey> REGISTRY = new ConcurrentHashMap<>();
  public static final Key<Function<ColorKey, Color>> FUNCTION_KEY = Key.create("COLOR_KEY_FUNCTION");

  public static @NotNull ColorKey find(@NotNull String externalName) {
    return REGISTRY.computeIfAbsent(externalName, name -> new ColorKey(name, null, null));
  }

  public static @NotNull ColorKey createColorKey(@NonNls @NotNull String externalName) {
    return find(externalName);
  }

  public static @NotNull ColorKey createColorKey(@NonNls @NotNull String externalName, @Nullable Color defaultColor) {
    // some crazy life cycle assumes we should overwrite default color
    // (e.g. when read from external schema HintUtil.INFORMATION_COLOR_KEY with null color, then try to re-create it with not-null color in HintUtil initializer).
    // The replacement is atomic: a concurrent find() between a remove and a put would leave the key without its default color.
    return REGISTRY.compute(externalName, (name, existingKey) -> {
      if (existingKey != null && Comparing.equal(existingKey.defaultColor, defaultColor)) {
        return existingKey;
      }
      ColorKey fallbackColorKey = existingKey == null ? null : existingKey.fallbackColorKey;
      return new ColorKey(name, defaultColor, fallbackColorKey);
    });
  }

  public static @NotNull ColorKey createColorKeyWithFallback(@NonNls @NotNull String externalName, @Nullable ColorKey fallbackColorKey) {
    // some crazy life cycle assumes we can overwrite color; the replacement is atomic, see createColorKey(String, Color)
    ColorKey key = REGISTRY.compute(externalName, (name, existingKey) -> {
      if (existingKey != null && Comparing.equal(existingKey.fallbackColorKey, fallbackColorKey)) {
        return existingKey;
      }
      Color defaultColor = existingKey == null ? null : existingKey.defaultColor;
      return new ColorKey(name, defaultColor, fallbackColorKey);
    });
    checkNoCycle(key, fallbackColorKey);
    return key;
  }

  private final @NotNull String externalName;
  private final @Nullable Color defaultColor;
  private final @Nullable ColorKey fallbackColorKey;

  private ColorKey(@NotNull String externalName, @Nullable Color defaultColor, @Nullable ColorKey fallbackColorKey) {
    this.externalName = externalName;
    this.defaultColor = defaultColor;
    this.fallbackColorKey = fallbackColorKey;
  }

  public @NotNull String getExternalName() {
    return externalName;
  }

  /** @noinspection NullableProblems */ // it breaks some kotlin code
  public /* @Nullable */ Color getDefaultColor() {
    return defaultColor;
  }

  public @Nullable ColorKey getFallbackColorKey() {
    return fallbackColorKey;
  }

  @Override
  public int compareTo(@NotNull ColorKey key) {
    return externalName.compareTo(key.externalName);
  }

  @Override
  public int hashCode() {
    return externalName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ColorKey && externalName.equals(((ColorKey)obj).externalName);
  }

  @Override
  public String toString() {
    return externalName;
  }

  private static void checkNoCycle(@NotNull ColorKey key, @Nullable ColorKey fallbackColorKey) {
    for (ColorKey current = fallbackColorKey; current != null; current = current.fallbackColorKey) {
      if (current.equals(key)) {
        LOG.error("Cycle detected: " + describeCycle(key, fallbackColorKey));
        return;
      }
    }
  }

  private static String describeCycle(ColorKey key, ColorKey fallbackColorKey) {
    StringBuilder cycle = new StringBuilder(key.externalName);
    for (ColorKey current = fallbackColorKey; current != null; current = current.fallbackColorKey) {
      cycle.append("->").append(current.externalName);
      if (current.equals(key)) {
        break;
      }
    }
    return cycle.toString();
  }
}
