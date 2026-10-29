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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AttributesFlyweight {
  /**
   * A lossy cache of some {@link #INTERNED} flyweights, indexed by the hash code.
   * A lookup that finds its flyweight here allocates nothing.
   * A miss fills only an empty slot, so after a warm-up the threads only read the array and do not contend.
   * A racy read is safe, because all fields of a flyweight are final.
   */
  private static final AttributesFlyweight[] FRONT = new AttributesFlyweight[1024];

  /**
   * The canonical flyweights. Each flyweight maps to itself, so a lookup with an equal candidate returns the canonical instance.
   * It is a map, not a set, because a set cannot return the element that it holds.
   */
  private static final ConcurrentMap<AttributesFlyweight, AttributesFlyweight> INTERNED = new ConcurrentHashMap<>();

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
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable Color errorStripeColor
  ) {
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, null, errorStripeColor);
  }

  @ApiStatus.Experimental
  public static @NotNull AttributesFlyweight create(
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable FontFamilies fontFamilies,
    @Nullable Color errorStripeColor
  ) {
    int hashCode =
      computeHashCode(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
    int slot = (hashCode ^ (hashCode >>> 16)) & (FRONT.length - 1);
    AttributesFlyweight cached = FRONT[slot];
    if (cached != null && cached.matches(
      foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor, hashCode
    )) {
      return cached;
    }
    AttributesFlyweight flyweight = intern(new AttributesFlyweight(
      foreground, background, fontType, effectColor, effectType, copyOf(additionalEffects), fontFamilies, errorStripeColor, hashCode
    ));
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
    @Nullable FontFamilies fontFamilies,
    Color errorStripeColor
  ) {
    int hashCode =
      computeHashCode(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
    return new AttributesFlyweight(
      foreground, background, fontType, effectColor, effectType, copyOf(additionalEffects), fontFamilies, errorStripeColor, hashCode
    );
  }

  private final Color foreground;
  private final Color background;
  private final @FontStyle int fontType;
  private final Color effectColor;
  private final EffectType effectType;
  private final @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects; // unmodifiable map
  private final @Nullable FontFamilies fontFamilies;
  private final Color errorStripeColor;
  private final int hashCode;

  private AttributesFlyweight(
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable FontFamilies fontFamilies,
    @Nullable Color errorStripeColor,
    int hashCode
  ) {
    this.foreground = foreground;
    this.background = background;
    this.fontType = fontType;
    this.effectColor = effectColor;
    this.effectType = effectType;
    this.additionalEffects = additionalEffects;
    this.fontFamilies = fontFamilies;
    this.errorStripeColor = errorStripeColor;
    this.hashCode = hashCode;
  }

  public Color getForeground() {
    return foreground;
  }

  public Color getBackground() {
    return background;
  }

  public @FontStyle int getFontType() {
    return fontType;
  }

  public Color getEffectColor() {
    return effectColor;
  }

  public EffectType getEffectType() {
    return effectType;
  }

  /**
   * @return true iff there are effects to draw in this attributes
   */
  @ApiStatus.Experimental
  public boolean hasEffects() {
    return effectColor != null && effectType != null || !additionalEffects.isEmpty();
  }

  /**
   * @return all attributes effects, main and additional ones
   */
  public @NotNull Map<EffectType, Color> getAllEffects() {
    if (additionalEffects.isEmpty()) {
      if (effectType == null || effectColor == null) {
        return Collections.emptyMap();
      }
      return Collections.singletonMap(effectType, effectColor);
    }
    TextAttributesEffectsBuilder builder = TextAttributesEffectsBuilder.create();
    additionalEffects.forEach(builder::coverWith);
    builder.coverWith(effectType, effectColor);
    return builder.getEffectsMap();
  }

  @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> getAdditionalEffects() {
    return additionalEffects;
  }

  @ApiStatus.Experimental
  public @Nullable FontFamilies getFontFamilies() {
    return fontFamilies;
  }

  Color getErrorStripeColor() {
    return errorStripeColor;
  }

  public @NotNull AttributesFlyweight withForeground(Color foreground) {
    if (ComparableColor.equalColors(foreground, this.foreground)) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
  }

  public @NotNull AttributesFlyweight withBackground(Color background) {
    if (ComparableColor.equalColors(background, this.background)) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
  }

  public @NotNull AttributesFlyweight withFontType(@FontStyle int fontType) {
    if (fontType == this.fontType) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
  }

  public @NotNull AttributesFlyweight withEffectColor(Color effectColor) {
    if (ComparableColor.equalColors(effectColor, this.effectColor)) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
  }

  public @NotNull AttributesFlyweight withEffectType(EffectType effectType) {
    if (Comparing.equal(effectType, this.effectType)) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
  }

  public @NotNull AttributesFlyweight withErrorStripeColor(Color stripeColor) {
    if (ComparableColor.equalColors(stripeColor, errorStripeColor)) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, stripeColor);
  }

  /**
   * @see TextAttributes#setAdditionalEffects(Map)
   */
  @ApiStatus.Experimental
  public @NotNull AttributesFlyweight withAdditionalEffects(@NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects) {
    if (Comparing.equal(additionalEffects, this.additionalEffects)) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
  }

  /**
   * @see TextAttributes#setFontFamilies(FontFamilies)
   */
  @ApiStatus.Experimental
  public @NotNull AttributesFlyweight withFontFamilies(@Nullable FontFamilies fontFamilies) {
    if (Objects.equals(fontFamilies, this.fontFamilies)) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
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
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable FontFamilies fontFamilies
  ) {
    if (hasFields(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor)) {
      return this;
    }
    return create(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AttributesFlyweight that = (AttributesFlyweight)o;
    return hasFields(
      that.foreground,
      that.background,
      that.fontType,
      that.effectColor,
      that.effectType,
      that.additionalEffects,
      that.fontFamilies,
      that.errorStripeColor
    );
  }

  /**
   * Whether this flyweight has the given fields and hash code. It compares the fields as {@link #equals} does.
   */
  private boolean matches(
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable FontFamilies fontFamilies,
    @Nullable Color errorStripeColor,
    int hashCode
  ) {
    return this.hashCode == hashCode &&
           hasFields(foreground, background, fontType, effectColor, effectType, additionalEffects, fontFamilies, errorStripeColor);
  }

  private boolean hasFields(
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable FontFamilies fontFamilies,
    @Nullable Color errorStripeColor
  ) {
    return this.fontType == fontType &&
           ComparableColor.equalColors(this.background, background) &&
           ComparableColor.equalColors(this.effectColor, effectColor) &&
           this.effectType == effectType &&
           ComparableColor.equalColors(this.errorStripeColor, errorStripeColor) &&
           ComparableColor.equalColors(this.foreground, foreground) &&
           Objects.equals(this.fontFamilies, fontFamilies) &&
           this.additionalEffects.equals(additionalEffects);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public @NonNls String toString() {
    return "AttributesFlyweight{foreground=" + foreground + ", background=" + background + ", fontType=" + fontType +
           ", effectColor=" + effectColor + ", effectType=" + effectType + ", fontFamilies=" + fontFamilies +
           ", errorStripeColor=" + errorStripeColor + '}';
  }

  /**
   * Returns the canonical flyweight that equals {@code candidate}. When there is none, {@code candidate} becomes canonical.
   */
  private static @NotNull AttributesFlyweight intern(@NotNull AttributesFlyweight candidate) {
    AttributesFlyweight interned = INTERNED.get(candidate);
    if (interned != null) {
      return interned;
    }
    AttributesFlyweight existing = INTERNED.putIfAbsent(candidate, candidate);
    return existing != null ? existing : candidate;
  }

  private static int computeHashCode(
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable FontFamilies fontFamilies,
    @Nullable Color errorStripeColor
  ) {
    int result = ComparableColor.colorHashCode(foreground);
    result = 31 * result + ComparableColor.colorHashCode(background);
    result = 31 * result + fontType;
    result = 31 * result + ComparableColor.colorHashCode(effectColor);
    result = 31 * result + Objects.hashCode(effectType);
    result = 31 * result + ComparableColor.colorHashCode(errorStripeColor);
    result = 31 * result + additionalEffects.hashCode();
    result = 31 * result + Objects.hashCode(fontFamilies);
    return result;
  }

  private static @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> copyOf(
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> effects
  ) {
    return effects.isEmpty() ? Collections.emptyMap() : new EnumMap<>(effects);
  }
}
