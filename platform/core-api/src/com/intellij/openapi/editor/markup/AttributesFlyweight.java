// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.ComparableColor;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AttributesFlyweight {
  /**
   * A lossy cache in front of {@link #ENTRIES}, indexed by the hash code. A lookup that finds its flyweight here allocates nothing.
   * A miss fills only an empty slot, so after a warm-up the threads only read the array and do not contend.
   * A racy read is safe, because all fields of a flyweight are final.
   */
  private static final AttributesFlyweight[] FRONT = new AttributesFlyweight[1024];

  private static final ConcurrentMap<AttributesFlyweight, AttributesFlyweight> ENTRIES = new ConcurrentHashMap<>();

  public static @NotNull AttributesFlyweight create(
    Color foreground,
    Color background,
    @FontStyle int fontType,
    Color effectColor,
    EffectType effectType,
    Color errorStripeColor
  ) {
    return create(foreground, background, fontType, effectColor, effectType, Collections.emptyMap(), errorStripeColor);
  }

  @ApiStatus.Experimental
  public static @NotNull AttributesFlyweight create(
    Color foreground,
    Color background,
    @FontStyle int fontType,
    Color effectColor,
    EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    Color errorStripeColor
  ) {
    int hashCode = computeHashCode(foreground, background, fontType, effectColor, effectType, additionalEffects, errorStripeColor);
    int slot = (hashCode ^ (hashCode >>> 16)) & (FRONT.length - 1);
    AttributesFlyweight cached = FRONT[slot];
    if (cached != null &&
        cached.matches(hashCode, foreground, background, fontType, effectColor, effectType, additionalEffects, errorStripeColor)) {
      return cached;
    }
    AttributesFlyweight candidate = new AttributesFlyweight(
      hashCode, foreground, background, fontType, effectColor, effectType, copyOf(additionalEffects), errorStripeColor
    );
    AttributesFlyweight flyweight = ENTRIES.get(candidate);
    if (flyweight == null) {
      AttributesFlyweight existing = ENTRIES.putIfAbsent(candidate, candidate);
      flyweight = existing != null ? existing : candidate;
    }
    if (cached == null) {
      FRONT[slot] = flyweight;
    }
    return flyweight;
  }

  @ApiStatus.Internal
  public static @NotNull AttributesFlyweight createNoCache(
    Color foreground,
    Color background,
    @FontStyle int fontType,
    Color effectColor,
    EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    Color errorStripeColor
  ) {
    int hashCode = computeHashCode(foreground, background, fontType, effectColor, effectType, additionalEffects, errorStripeColor);
    return new AttributesFlyweight(
      hashCode, foreground, background, fontType, effectColor, effectType, copyOf(additionalEffects), errorStripeColor
    );
  }

  private final int myHashCode;
  private final Color myForeground;
  private final Color myBackground;
  private final @FontStyle int myFontType;
  private final Color myEffectColor;
  private final EffectType myEffectType;
  private final @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> myAdditionalEffects; // unmodifiable map
  private final Color myErrorStripeColor;

  private AttributesFlyweight(
    int hashCode,
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable Color errorStripeColor
  ) {
    myForeground = foreground;
    myBackground = background;
    myFontType = fontType;
    myEffectColor = effectColor;
    myEffectType = effectType;
    myErrorStripeColor = errorStripeColor;
    myAdditionalEffects = additionalEffects;
    myHashCode = hashCode;
  }

  public Color getForeground() {
    return myForeground;
  }

  public Color getBackground() {
    return myBackground;
  }

  public @FontStyle int getFontType() {
    return myFontType;
  }

  public Color getEffectColor() {
    return myEffectColor;
  }

  public EffectType getEffectType() {
    return myEffectType;
  }

  /**
   * @return true iff there are effects to draw in this attributes
   */
  @ApiStatus.Experimental
  public boolean hasEffects() {
    return myEffectColor != null && myEffectType != null || !myAdditionalEffects.isEmpty();
  }

  /**
   * @return all attributes effects, main and additional ones
   */
  public @NotNull Map<EffectType, Color> getAllEffects() {
    if (myAdditionalEffects.isEmpty()) {
      if (myEffectType == null || myEffectColor == null) {
        return Collections.emptyMap();
      }
      return Collections.singletonMap(myEffectType, myEffectColor);
    }
    TextAttributesEffectsBuilder builder = TextAttributesEffectsBuilder.create();
    myAdditionalEffects.forEach(builder::coverWith);
    builder.coverWith(myEffectType, myEffectColor);
    return builder.getEffectsMap();
  }

  @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> getAdditionalEffects() {
    return myAdditionalEffects;
  }

  Color getErrorStripeColor() {
    return myErrorStripeColor;
  }

  public @NotNull AttributesFlyweight withForeground(Color foreground) {
    if (ComparableColor.equalColors(foreground, myForeground)) {
      return this;
    }
    return create(foreground, myBackground, myFontType, myEffectColor, myEffectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withBackground(Color background) {
    if (ComparableColor.equalColors(background, myBackground)) {
      return this;
    }
    return create(myForeground, background, myFontType, myEffectColor, myEffectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withFontType(@FontStyle int fontType) {
    if (fontType == myFontType) {
      return this;
    }
    return create(myForeground, myBackground, fontType, myEffectColor, myEffectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withEffectColor(Color effectColor) {
    if (ComparableColor.equalColors(effectColor, myEffectColor)) {
      return this;
    }
    return create(myForeground, myBackground, myFontType, effectColor, myEffectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withEffectType(EffectType effectType) {
    if (Comparing.equal(effectType, myEffectType)) {
      return this;
    }
    return create(myForeground, myBackground, myFontType, myEffectColor, effectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withErrorStripeColor(Color stripeColor) {
    if (ComparableColor.equalColors(stripeColor, myErrorStripeColor)) {
      return this;
    }
    return create(myForeground, myBackground, myFontType, myEffectColor, myEffectType, myAdditionalEffects, stripeColor);
  }

  /**
   * @see TextAttributes#setAdditionalEffects(Map)
   */
  @ApiStatus.Experimental
  public @NotNull AttributesFlyweight withAdditionalEffects(@NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects) {
    if (Comparing.equal(additionalEffects, myAdditionalEffects)) {
      return this;
    }
    return create(myForeground, myBackground, myFontType, myEffectColor, myEffectType, additionalEffects, myErrorStripeColor);
  }

  /**
   * Returns a flyweight with the given fields and the error stripe color of this flyweight.
   * Unlike a chain of the {@code with} methods, it looks up the cache at most once.
   */
  @NotNull AttributesFlyweight with(
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects
  ) {
    if (hasFields(foreground, background, fontType, effectColor, effectType, additionalEffects, myErrorStripeColor)) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, myErrorStripeColor);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AttributesFlyweight that = (AttributesFlyweight)o;
    return hasFields(
      that.myForeground, that.myBackground, that.myFontType, that.myEffectColor, that.myEffectType, that.myAdditionalEffects,
      that.myErrorStripeColor
    );
  }

  /**
   * Whether this flyweight has the given hash code and fields. It compares the fields as {@link #equals} does.
   */
  private boolean matches(
    int hashCode,
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable Color errorStripeColor
  ) {
    return myHashCode == hashCode &&
           hasFields(foreground, background, fontType, effectColor, effectType, additionalEffects, errorStripeColor);
  }

  private boolean hasFields(
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable Color errorStripeColor
  ) {
    return myFontType == fontType &&
           ComparableColor.equalColors(myBackground, background) &&
           ComparableColor.equalColors(myEffectColor, effectColor) &&
           myEffectType == effectType &&
           ComparableColor.equalColors(myErrorStripeColor, errorStripeColor) &&
           ComparableColor.equalColors(myForeground, foreground) &&
           myAdditionalEffects.equals(additionalEffects);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  @Override
  public @NonNls String toString() {
    return "AttributesFlyweight{myForeground=" + myForeground + ", myBackground=" + myBackground + ", myFontType=" + myFontType +
           ", myEffectColor=" + myEffectColor + ", myEffectType=" + myEffectType + ", myErrorStripeColor=" + myErrorStripeColor + '}';
  }

  private static int computeHashCode(
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable Color errorStripeColor
  ) {
    int result = ComparableColor.colorHashCode(foreground);
    result = 31 * result + ComparableColor.colorHashCode(background);
    result = 31 * result + fontType;
    result = 31 * result + ComparableColor.colorHashCode(effectColor);
    result = 31 * result + (effectType != null ? effectType.hashCode() : 0);
    result = 31 * result + ComparableColor.colorHashCode(errorStripeColor);
    result = 31 * result + additionalEffects.hashCode();
    return result;
  }

  private static @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> copyOf(
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> effects
  ) {
    return effects.isEmpty() ? Collections.emptyMap() : new EnumMap<>(effects);
  }
}
