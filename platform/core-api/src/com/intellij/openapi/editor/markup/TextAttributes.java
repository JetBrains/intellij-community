// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Defines the visual representation (colors and effects) of a text.
 */
public class TextAttributes implements Cloneable {
  private static final Logger LOG = Logger.getInstance(TextAttributes.class);
  private static final AttributesFlyweight DEFAULT_FLYWEIGHT = AttributesFlyweight.create(
    null,
    null,
    Font.PLAIN,
    null,
    EffectType.BOXED,
    Collections.emptyMap(),
    null
  );

  public static final TextAttributes ERASE_MARKER = new TextAttributes() {
    @Override
    public String toString() {
      return "[ERASE_MARKER]";
    }
  };

  /**
   * Merges (layers) the two given text attributes.
   *
   * @param under Text attributes to merge "under".
   * @param above Text attributes to merge "above", overriding settings from "under".
   * @return Merged attributes instance.
   */
  @Contract("!null, !null -> !null")
  public static TextAttributes merge(@Nullable TextAttributes under, @Nullable TextAttributes above) {
    if (under == null) return above;
    if (above == null) return under;
    TextAttributes merged = under.clone();
    if (merged.getClass() != TextAttributes.class) {
      // a subclass can override the setters, so it gets one setter call for each field
      if (above.getBackgroundColor() != null) {
        merged.setBackgroundColor(above.getBackgroundColor());
      }
      if (above.getForegroundColor() != null) {
        merged.setForegroundColor(above.getForegroundColor());
      }
      if (above.getFontFamilies() != null) {
        merged.setFontFamilies(above.getFontFamilies());
      }
      merged.setFontType(above.getFontType() | under.getFontType());
      TextAttributesEffectsBuilder.create(under).coverWith(above).applyTo(merged);
      return merged;
    }
    AttributesFlyweight flyweight = merged.flyweight;
    Color background = above.getBackgroundColor();
    Color foreground = above.getForegroundColor();
    FontFamilies fontFamilies = above.getFontFamilies();
    int fontType = validFontType(above.getFontType() | under.getFontType());
    AttributesFlyweight mergedFlyweight = TextAttributesEffectsBuilder.create(under)
      .coverWith(above)
      .applyTo(
        flyweight,
        foreground != null ? foreground : flyweight.getForeground(),
        background != null ? background : flyweight.getBackground(),
        fontType,
        fontFamilies != null ? fontFamilies : flyweight.getFontFamilies()
      );
    merged.setFlyweight(mergedFlyweight);
    return merged;
  }

  public static @NotNull TextAttributes fromFlyweight(@NotNull AttributesFlyweight flyweight) {
    return new TextAttributes(flyweight);
  }

  private @NotNull AttributesFlyweight flyweight;

  public TextAttributes() {
    this(DEFAULT_FLYWEIGHT);
  }

  public TextAttributes(
    @Nullable Color foregroundColor,
    @Nullable Color backgroundColor,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @FontStyle int fontType
  ) {
    this(AttributesFlyweight.create(
      foregroundColor,
      backgroundColor,
      fontType,
      effectColor,
      effectType,
      Collections.emptyMap(),
      null
    ));
  }

  public TextAttributes(@NotNull Element element) {
    this(AttributesFlyweightSerDe.create(element));
  }

  @ApiStatus.Internal
  public TextAttributes(@NotNull DataInput in) throws IOException {
    this(AttributesFlyweightSerDe.create(in));
  }

  private TextAttributes(@NotNull AttributesFlyweight flyweight) {
    this.flyweight = flyweight;
  }

  /**
   * Replaces all fields. The additional effects become empty, and the font families become {@code null}.
   */
  public void setAttributes(
    Color foregroundColor,
    Color backgroundColor,
    Color effectColor,
    Color errorStripeColor,
    EffectType effectType,
    @FontStyle int fontType
  ) {
    setFlyweight(AttributesFlyweight.create(
      foregroundColor,
      backgroundColor,
      fontType,
      effectColor,
      effectType,
      Collections.emptyMap(),
      errorStripeColor
    ));
  }

  /**
   * Replaces all fields, and bypasses the flyweight cache. The additional effects become empty.
   */
  @ApiStatus.Internal
  public void setAttributesNoCache(
    Color foregroundColor,
    Color backgroundColor,
    Color effectColor,
    Color errorStripeColor,
    EffectType effectType,
    @FontStyle int fontType,
    @Nullable FontFamilies fontFamilies
  ) {
    setFlyweight(AttributesFlyweight.createNoCache(
      foregroundColor,
      backgroundColor,
      fontType,
      effectColor,
      effectType,
      Collections.emptyMap(),
      fontFamilies,
      errorStripeColor
    ));
  }

  public @NotNull AttributesFlyweight getFlyweight() {
    return flyweight;
  }

  final void setFlyweight(@NotNull AttributesFlyweight flyweight) {
    this.flyweight = flyweight;
  }

  public void copyFrom(@NotNull TextAttributes other) {
    setFlyweight(other.flyweight);
  }

  public boolean isEmpty() {
    return getForegroundColor() == null &&
           getBackgroundColor() == null &&
           getEffectColor() == null &&
           getFontType() == Font.PLAIN &&
           getFontFamilies() == null;
  }

  public Color getForegroundColor() {
    return flyweight.getForeground();
  }

  public void setForegroundColor(Color color) {
    setFlyweight(flyweight.withForeground(color));
  }

  public Color getBackgroundColor() {
    return flyweight.getBackground();
  }

  public void setBackgroundColor(Color color) {
    setFlyweight(flyweight.withBackground(color));
  }

  public Color getEffectColor() {
    return flyweight.getEffectColor();
  }

  public void setEffectColor(Color color) {
    setFlyweight(flyweight.withEffectColor(color));
  }

  public Color getErrorStripeColor() {
    return flyweight.getErrorStripeColor();
  }

  public void setErrorStripeColor(Color color) {
    setFlyweight(flyweight.withErrorStripeColor(color));
  }

  public @FontStyle int getFontType() {
    return flyweight.getFontType();
  }

  public void setFontType(@FontStyle int type) {
    setFlyweight(flyweight.withFontType(validFontType(type)));
  }

  /**
   * @return true iff there are effects to draw in this attributes
   */
  @ApiStatus.Experimental
  public boolean hasEffects() {
    return flyweight.hasEffects();
  }

  /**
   * Sets additional effects to paint
   * @param effectsMap map of effect types and colors to use.
   */
  @ApiStatus.Experimental
  public void setAdditionalEffects(@NotNull Map<@NotNull EffectType, ? extends @NotNull Color> effectsMap) {
    setFlyweight(flyweight.withAdditionalEffects(effectsMap));
  }

  @ApiStatus.Internal
  public Map<@NotNull EffectType, ? extends @NotNull Color> getAdditionalEffects() {
    return Collections.unmodifiableMap(flyweight.getAdditionalEffects());
  }

  public @Nullable EffectType getEffectType() {
    return flyweight.getEffectType();
  }

  public void setEffectType(EffectType effectType) {
    setFlyweight(flyweight.withEffectType(effectType));
  }

  @ApiStatus.Experimental
  public void forEachAdditionalEffect(@NotNull BiConsumer<? super EffectType, ? super Color> consumer) {
    flyweight.getAdditionalEffects().forEach(consumer);
  }

  @ApiStatus.Experimental
  public void forEachEffect(@NotNull BiConsumer<? super EffectType, ? super Color> consumer) {
    flyweight.getAllEffects().forEach(consumer);
  }

  /**
   * Appends additional effect to paint with specific color
   *
   * @see TextAttributes#setAdditionalEffects(Map)
   */
  @ApiStatus.Experimental
  public void withAdditionalEffect(@NotNull EffectType effectType, @NotNull Color color) {
    TextAttributesEffectsBuilder
      .create(this)
      .coverWith(effectType, color)
      .applyTo(this);
  }

  @ApiStatus.Experimental
  public @Nullable FontFamilies getFontFamilies() {
    return flyweight.getFontFamilies();
  }

  @ApiStatus.Experimental
  public void setFontFamilies(@Nullable FontFamilies fontFamilies) {
    setFlyweight(flyweight.withFontFamilies(fontFamilies));
  }

  public void readExternal(@NotNull Element element) {
    setFlyweight(AttributesFlyweightSerDe.create(element));
  }

  public void writeExternal(Element element) {
    AttributesFlyweightSerDe.writeExternal(flyweight, element);
  }

  @ApiStatus.Internal
  public void readExternal(@NotNull DataInput in) throws IOException {
    setFlyweight(AttributesFlyweightSerDe.create(in));
  }

  @ApiStatus.Internal
  public void writeExternal(@NotNull DataOutput out) throws IOException {
    AttributesFlyweightSerDe.writeExternal(flyweight, out);
  }

  /** @noinspection MethodDoesntCallSuperMethod*/
  @Override
  public TextAttributes clone() {
    return new TextAttributes(flyweight);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TextAttributes)) {
      return false;
    }
    return Objects.equals(flyweight, ((TextAttributes)obj).flyweight);
  }

  @Override
  public int hashCode() {
    return flyweight.hashCode();
  }

  @Override
  public String toString() {
    return "[fore=" + getForegroundColor() + ", back=" + getBackgroundColor()
           + ", type=" + getFontType()
           + (getEffectType() == null ? "" : ", effect=" + getEffectType())
           + (getEffectColor() == null ? "" : ", effect color=" + getEffectColor())
           + (flyweight.getAdditionalEffects().isEmpty() ? "" : ", additional=" + flyweight.getAdditionalEffects())
           + (getFontFamilies() == null ? "" : ", families=" + getFontFamilies())
           + (getErrorStripeColor() == null ? "" : ", stripe=" + getErrorStripeColor())
           + "]";
  }

  private static @FontStyle int validFontType(@FontStyle int type) {
    if (type < 0 || type > 3) {
      LOG.error("Wrong font type: " + type);
      return Font.PLAIN;
    }
    return type;
  }
}
