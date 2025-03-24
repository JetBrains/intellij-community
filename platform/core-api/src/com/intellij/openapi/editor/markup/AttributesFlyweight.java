// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ui.ComparableColor;
import org.intellij.lang.annotations.JdkConstants;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.util.io.DataInputOutputUtil.readINT;
import static com.intellij.util.io.DataInputOutputUtil.writeINT;

public final class AttributesFlyweight {
  private static final ConcurrentMap<FlyweightKey, AttributesFlyweight> entries = new ConcurrentHashMap<>();
  private static final ThreadLocal<FlyweightKey> ourKey = new ThreadLocal<>();

  private final int myHashCode;
  private final Color myForeground;
  private final Color myBackground;
  @JdkConstants.FontStyle
  private final int myFontType;
  private final Color myEffectColor;
  private final EffectType myEffectType;
  private final @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> myAdditionalEffects; // unmodifiable map
  private final Color myErrorStripeColor;

  private static final class FlyweightKey implements Cloneable {
    private Color foreground;
    private Color background;
    @JdkConstants.FontStyle
    private int fontType;
    private Color effectColor;
    private EffectType effectType;
    private Color errorStripeColor;
    private @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> myAdditionalEffects = Collections.emptyMap();

    private FlyweightKey() {
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FlyweightKey)) return false;

      FlyweightKey key = (FlyweightKey)o;

      if (fontType != key.fontType) return false;
      if (!ComparableColor.equalColors(background, key.background)) return false;
      if (!ComparableColor.equalColors(effectColor, key.effectColor)) return false;
      if (effectType != key.effectType) return false;
      if (!ComparableColor.equalColors(errorStripeColor, key.errorStripeColor)) return false;
      if (!ComparableColor.equalColors(foreground, key.foreground)) return false;
      if (!myAdditionalEffects.equals(key.myAdditionalEffects)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = ComparableColor.colorHashCode(foreground);
      result = 31 * result + ComparableColor.colorHashCode(background);
      result = 31 * result + fontType;
      result = 31 * result + ComparableColor.colorHashCode(effectColor);
      result = 31 * result + (effectType != null ? effectType.hashCode() : 0);
      result = 31 * result + ComparableColor.colorHashCode(errorStripeColor);
      result = 31 * result + myAdditionalEffects.hashCode();
      return result;
    }

    @Override
    protected FlyweightKey clone() {
      try {
        return (FlyweightKey)super.clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static @NotNull AttributesFlyweight create(Color foreground,
                                                    Color background,
                                                    @JdkConstants.FontStyle int fontType,
                                                    Color effectColor,
                                                    EffectType effectType,
                                                    Color errorStripeColor) {
    return create(foreground, background, fontType, effectColor, effectType, Collections.emptyMap(), errorStripeColor);
  }

  @ApiStatus.Experimental
  public static @NotNull AttributesFlyweight create(Color foreground,
                                                    Color background,
                                                    @JdkConstants.FontStyle int fontType,
                                                    Color effectColor,
                                                    EffectType effectType,
                                                    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
                                                    Color errorStripeColor) {
    FlyweightKey key = ourKey.get();
    if (key == null) {
      ourKey.set(key = new FlyweightKey());
    }
    key.foreground = foreground;
    key.background = background;
    key.fontType = fontType;
    key.effectColor = effectColor;
    key.effectType = effectType;
    key.myAdditionalEffects = additionalEffects.isEmpty() ? Collections.emptyMap() : new EnumMap<>(additionalEffects);
    key.errorStripeColor = errorStripeColor;

    AttributesFlyweight flyweight = entries.get(key);
    if (flyweight != null) {
      return flyweight;
    }

    return ConcurrencyUtil.cacheOrGet(entries, key.clone(), new AttributesFlyweight(key));
  }

  private AttributesFlyweight(@NotNull FlyweightKey key) {
    myForeground = key.foreground;
    myBackground = key.background;
    myFontType = key.fontType;
    myEffectColor = key.effectColor;
    myEffectType = key.effectType;
    myErrorStripeColor = key.errorStripeColor;
    myAdditionalEffects = key.myAdditionalEffects;
    myHashCode = key.hashCode();
  }

  static @NotNull AttributesFlyweight create(@NotNull Element element) throws InvalidDataException {
    Color FOREGROUND = DefaultJDOMExternalizer.toColor(JDOMExternalizerUtil.readField(element, "FOREGROUND"));
    Color BACKGROUND = DefaultJDOMExternalizer.toColor(JDOMExternalizerUtil.readField(element, "BACKGROUND"));
    Color EFFECT_COLOR = DefaultJDOMExternalizer.toColor(JDOMExternalizerUtil.readField(element, "EFFECT_COLOR"));
    Color ERROR_STRIPE_COLOR = DefaultJDOMExternalizer.toColor(JDOMExternalizerUtil.readField(element, "ERROR_STRIPE_COLOR"));
    int fontType = DefaultJDOMExternalizer.toInt(JDOMExternalizerUtil.readField(element, "FONT_TYPE", "0"));
    if (fontType < 0 || fontType > 3) {
      fontType = 0;
    }
    int FONT_TYPE = fontType;
    int EFFECT_TYPE = DefaultJDOMExternalizer.toInt(JDOMExternalizerUtil.readField(element, "EFFECT_TYPE", "0"));
    // todo additionalEffects are not serialized yet, we have no user-controlled additional effects
    return create(FOREGROUND, BACKGROUND, FONT_TYPE, EFFECT_COLOR, toEffectType(EFFECT_TYPE), Collections.emptyMap(), ERROR_STRIPE_COLOR);
  }

  @ApiStatus.Internal
  static @NotNull AttributesFlyweight create(@NotNull DataInput in) throws IOException {
    Color FOREGROUND = readColor(in);
    Color BACKGROUND = readColor(in);
    int fontType = readINT(in);
    if (fontType < 0 || fontType > 3) {
      fontType = 0;
    }
    int FONT_TYPE = fontType;
    Color EFFECT_COLOR = readColor(in);
    Color ERROR_STRIPE_COLOR = readColor(in);
    int EFFECT_TYPE = readINT(in);
    return create(FOREGROUND, BACKGROUND, FONT_TYPE, EFFECT_COLOR, toEffectType(EFFECT_TYPE), Collections.emptyMap(), ERROR_STRIPE_COLOR);
  }

  private static @Nullable Color readColor(@NotNull DataInput in) throws IOException {
    boolean colorExists = in.readBoolean();
    return colorExists ? new Color(readINT(in)) : null;
  }

  private static void writeColor(@NotNull Element element, @NotNull String fieldName, Color color) {
    if (color != null) {
      String string = Integer.toString(color.getRGB() & 0xFFFFFF, 16);
      JDOMExternalizerUtil.writeField(element, fieldName, string);
    }
  }

  private static void writeColor(@NotNull DataOutput out, @Nullable Color color) throws IOException {
    boolean colorExists = color != null;
    out.writeBoolean(colorExists);
    if (colorExists) {
      writeINT(out, color.getRGB() & 0xFFFFFF);
    }
  }

  void writeExternal(@NotNull Element element) {
    writeColor(element, "FOREGROUND", getForeground());
    writeColor(element, "BACKGROUND", getBackground());
    int fontType = getFontType();
    if (fontType != 0) {
      JDOMExternalizerUtil.writeField(element, "FONT_TYPE", String.valueOf(fontType));
    }
    writeColor(element, "EFFECT_COLOR", getEffectColor());
    writeColor(element, "ERROR_STRIPE_COLOR", getErrorStripeColor());
    int effectType = fromEffectType(getEffectType());
    if (effectType != 0) {
      JDOMExternalizerUtil.writeField(element, "EFFECT_TYPE", String.valueOf(effectType));
    }
    // todo additionalEffects are not serialized yet, we have no user-controlled additional effects
  }

  @ApiStatus.Internal
  void writeExternal(@NotNull DataOutput out) throws IOException {
    writeColor(out, getForeground());
    writeColor(out, getBackground());
    writeINT(out, getFontType());
    writeColor(out, getEffectColor());
    writeColor(out, getErrorStripeColor());
    writeINT(out, fromEffectType(getEffectType()));
  }

  private static final int EFFECT_BORDER = 0;
  private static final int EFFECT_LINE = 1;
  private static final int EFFECT_WAVE = 2;
  private static final int EFFECT_STRIKEOUT = 3;
  private static final int EFFECT_BOLD_LINE = 4;
  private static final int EFFECT_BOLD_DOTTED_LINE = 5;

  private static int fromEffectType(EffectType effectType) {
    if (effectType == null) return -1;
    switch (effectType) {
      case BOXED: return EFFECT_BORDER;
      case LINE_UNDERSCORE: return EFFECT_LINE;
      case BOLD_LINE_UNDERSCORE: return EFFECT_BOLD_LINE;
      case STRIKEOUT: return EFFECT_STRIKEOUT;
      case WAVE_UNDERSCORE: return EFFECT_WAVE;
      case BOLD_DOTTED_LINE: return EFFECT_BOLD_DOTTED_LINE;
      default: return -1;
    }
  }

  private static EffectType toEffectType(int effectType) {
    switch (effectType) {
      case EFFECT_BORDER: return EffectType.BOXED;
      case EFFECT_BOLD_LINE: return EffectType.BOLD_LINE_UNDERSCORE;
      case EFFECT_LINE: return EffectType.LINE_UNDERSCORE;
      case EFFECT_STRIKEOUT: return EffectType.STRIKEOUT;
      case EFFECT_WAVE: return EffectType.WAVE_UNDERSCORE;
      case EFFECT_BOLD_DOTTED_LINE: return EffectType.BOLD_DOTTED_LINE;
      default: return null;
    }
  }

  public Color getForeground() {
    return myForeground;
  }

  public Color getBackground() {
    return myBackground;
  }

  @JdkConstants.FontStyle
  public int getFontType() {
    return myFontType;
  }

  public Color getEffectColor() {
    return myEffectColor;
  }

  public EffectType getEffectType() {
    return myEffectType;
  }

  @NotNull
  Map<@NotNull EffectType, ? extends @NotNull Color> getAdditionalEffects() {
    return myAdditionalEffects;
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
      return myEffectType == null || myEffectColor == null ? Collections.emptyMap() : Collections.singletonMap(myEffectType, myEffectColor);
    }
    TextAttributesEffectsBuilder builder = TextAttributesEffectsBuilder.create();
    myAdditionalEffects.forEach(builder::coverWith);
    builder.coverWith(myEffectType, myEffectColor);
    return builder.getEffectsMap();
  }

  Color getErrorStripeColor() {
    return myErrorStripeColor;
  }

  public @NotNull AttributesFlyweight withForeground(Color foreground) {
    return ComparableColor.equalColors(foreground, myForeground)
           ? this
           : create(foreground, myBackground, myFontType, myEffectColor, myEffectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withBackground(Color background) {
    return ComparableColor.equalColors(background, myBackground)
           ? this
           : create(myForeground, background, myFontType, myEffectColor, myEffectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withFontType(@JdkConstants.FontStyle int fontType) {
    return fontType == myFontType
           ? this
           : create(myForeground, myBackground, fontType, myEffectColor, myEffectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withEffectColor(Color effectColor) {
    return ComparableColor.equalColors(effectColor, myEffectColor)
           ? this
           : create(myForeground, myBackground, myFontType, effectColor, myEffectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withEffectType(EffectType effectType) {
    return Comparing.equal(effectType, myEffectType)
           ? this
           : create(myForeground, myBackground, myFontType, myEffectColor, effectType, myAdditionalEffects, myErrorStripeColor);
  }

  public @NotNull AttributesFlyweight withErrorStripeColor(Color stripeColor) {
    return ComparableColor.equalColors(stripeColor, myErrorStripeColor)
           ? this
           : create(myForeground, myBackground, myFontType, myEffectColor, myEffectType, myAdditionalEffects, stripeColor);
  }

  /**
   * @see TextAttributes#setAdditionalEffects(Map)
   */
  @ApiStatus.Experimental
  public @NotNull AttributesFlyweight withAdditionalEffects(@NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects) {
    return Comparing.equal(additionalEffects, myAdditionalEffects)
           ? this
           : create(myForeground, myBackground, myFontType, myEffectColor, myEffectType, additionalEffects, myErrorStripeColor);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AttributesFlyweight that = (AttributesFlyweight)o;

    if (myFontType != that.myFontType) return false;
    if (!ComparableColor.equalColors(myBackground, that.myBackground)) return false;
    if (!ComparableColor.equalColors(myEffectColor, that.myEffectColor)) return false;
    if (myEffectType != that.myEffectType) return false;
    if (!ComparableColor.equalColors(myErrorStripeColor, that.myErrorStripeColor)) return false;
    if (!ComparableColor.equalColors(myForeground, that.myForeground)) return false;
    if (!myAdditionalEffects.equals(that.myAdditionalEffects)) return false;

    return true;
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
}
