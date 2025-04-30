// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.diagnostic.Logger;
import org.intellij.lang.annotations.JdkConstants;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
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
  private static final AttributesFlyweight DEFAULT_FLYWEIGHT = AttributesFlyweight
    .create(null, null, Font.PLAIN, null, EffectType.BOXED, Collections.emptyMap(), null);

  public static final TextAttributes ERASE_MARKER = new TextAttributes() {
    @Override
    public String toString() {
      return "[ERASE_MARKER]";
    }
  };

  @SuppressWarnings("NotNullFieldNotInitialized") private @NotNull AttributesFlyweight attrs;

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

    TextAttributes attrs = under.clone();
    if (above.getBackgroundColor() != null){
      attrs.setBackgroundColor(above.getBackgroundColor());
    }
    if (above.getForegroundColor() != null){
      attrs.setForegroundColor(above.getForegroundColor());
    }
    attrs.setFontType(above.getFontType() | under.getFontType());

    TextAttributesEffectsBuilder.create(under).coverWith(above).applyTo(attrs);

    return attrs;
  }

  public TextAttributes() {
    this(DEFAULT_FLYWEIGHT);
  }

  private TextAttributes(@NotNull AttributesFlyweight attributesFlyweight) {
    attrs = attributesFlyweight;
  }

  public TextAttributes(@NotNull Element element) {
    readExternal(element);
  }

  @ApiStatus.Internal
  public TextAttributes(@NotNull DataInput in) throws IOException {
    readExternal(in);
  }

  public TextAttributes(
    @Nullable Color foregroundColor,
    @Nullable Color backgroundColor,
    @Nullable Color effectColor,
    EffectType effectType,
    @JdkConstants.FontStyle int fontType
  ) {
    setAttributes(foregroundColor, backgroundColor, effectColor, null, effectType, fontType);
  }

  public void copyFrom(@NotNull TextAttributes other) {
    attrs = other.attrs;
  }

  public void setAttributes(Color foregroundColor,
                            Color backgroundColor,
                            Color effectColor,
                            Color errorStripeColor,
                            EffectType effectType,
                            @JdkConstants.FontStyle int fontType) {
    attrs = AttributesFlyweight
      .create(foregroundColor, backgroundColor, fontType, effectColor, effectType, Collections.emptyMap(), errorStripeColor);
  }

  @ApiStatus.Internal
  public void setAttributesNoCache(Color foregroundColor,
                                   Color backgroundColor,
                                   Color effectColor,
                                   Color errorStripeColor,
                                   EffectType effectType,
                                   @JdkConstants.FontStyle int fontType) {
    attrs = AttributesFlyweight
      .createNoCache(foregroundColor, backgroundColor, fontType, effectColor, effectType, Collections.emptyMap(), errorStripeColor);
  }

  public boolean isEmpty(){
    return getForegroundColor() == null && getBackgroundColor() == null && getEffectColor() == null && getFontType() == Font.PLAIN;
  }

  public @NotNull AttributesFlyweight getFlyweight() {
    return attrs;
  }

  public static @NotNull TextAttributes fromFlyweight(@NotNull AttributesFlyweight flyweight) {
    return new TextAttributes(flyweight);
  }

  public Color getForegroundColor() {
    return attrs.getForeground();
  }

  public void setForegroundColor(Color color) {
    attrs = attrs.withForeground(color);
  }

  public Color getBackgroundColor() {
    return attrs.getBackground();
  }

  public void setBackgroundColor(Color color) {
    attrs = attrs.withBackground(color);
  }

  public Color getEffectColor() {
    return attrs.getEffectColor();
  }

  public void setEffectColor(Color color) {
    attrs = attrs.withEffectColor(color);
  }

  public Color getErrorStripeColor() {
    return attrs.getErrorStripeColor();
  }

  public void setErrorStripeColor(Color color) {
    attrs = attrs.withErrorStripeColor(color);
  }

  /**
   * @return true iff there are effects to draw in this attributes
   */
  @ApiStatus.Experimental
  public boolean hasEffects() {
    return attrs.hasEffects();
  }

  /**
   * Sets additional effects to paint
   * @param effectsMap map of effect types and colors to use.
   */
  @ApiStatus.Experimental
  public void setAdditionalEffects(@NotNull Map<@NotNull EffectType, ? extends @NotNull Color> effectsMap) {
    attrs = attrs.withAdditionalEffects(effectsMap);
  }

  @ApiStatus.Internal
  public Map<@NotNull EffectType, ? extends @NotNull Color> getAdditionalEffects() {
    return Collections.unmodifiableMap(attrs.getAdditionalEffects());
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

  public @Nullable EffectType getEffectType() {
    return attrs.getEffectType();
  }

  @ApiStatus.Experimental
  public void forEachAdditionalEffect(@NotNull BiConsumer<? super EffectType, ? super Color> consumer) {
    attrs.getAdditionalEffects().forEach(consumer);
  }

  @ApiStatus.Experimental
  public void forEachEffect(@NotNull BiConsumer<? super EffectType, ? super Color> consumer) {
    attrs.getAllEffects().forEach(consumer);
  }

  public void setEffectType(EffectType effectType) {
    attrs = attrs.withEffectType(effectType);
  }

  @JdkConstants.FontStyle
  public int getFontType() {
    return attrs.getFontType();
  }

  public void setFontType(@JdkConstants.FontStyle int type) {
    if (type < 0 || type > 3) {
      LOG.error("Wrong font type: " + type);
      type = Font.PLAIN;
    }
    attrs = attrs.withFontType(type);
  }

  /** @noinspection MethodDoesntCallSuperMethod*/
  @Override
  public TextAttributes clone() {
    return new TextAttributes(attrs);
  }

  @Override
  public boolean equals(Object obj) {
    if(!(obj instanceof TextAttributes)) {
      return false;
    }
    return Objects.equals(attrs, ((TextAttributes)obj).attrs);
  }

  @Override
  public int hashCode() {
    return attrs.hashCode();
  }

  public void readExternal(@NotNull Element element) {
    attrs = AttributesFlyweight.create(element);
  }

  @ApiStatus.Internal
  public void readExternal(@NotNull DataInput in) throws IOException {
    attrs = AttributesFlyweight.create(in);
  }

  public void writeExternal(Element element) {
    attrs.writeExternal(element);
  }

  @ApiStatus.Internal
  public void writeExternal(@NotNull DataOutput out) throws IOException {
    attrs.writeExternal(out);
  }

  @Override
  public String toString() {
    return "[fore=" + getForegroundColor() + ", back=" + getBackgroundColor()
           + ", type=" + getFontType()
           + (getEffectType() == null ? "" : ", effect=" + getEffectType())
           + (getEffectColor() == null ? "" : ", effect color=" + getEffectColor())
           + (attrs.getAdditionalEffects().isEmpty() ? "" : ", additional=" + attrs.getAdditionalEffects())
           + (getErrorStripeColor() == null ? "" : ", stripe=" + getErrorStripeColor())
           + "]";
  }
}
