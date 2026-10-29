// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorHexUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ui.ComparableColor;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
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
  private static final ConcurrentMap<FlyweightKey, AttributesFlyweight> ENTRIES = new ConcurrentHashMap<>();
  private static final ThreadLocal<FlyweightKey> LOCAL_KEY = new ThreadLocal<>();

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
    FlyweightKey key = createKey(foreground, background, fontType, effectColor, effectType, additionalEffects, errorStripeColor);
    AttributesFlyweight flyweight = ENTRIES.get(key);
    if (flyweight != null) {
      return flyweight;
    }
    return ConcurrencyUtil.cacheOrGet(ENTRIES, key.clone(), new AttributesFlyweight(key));
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
    FlyweightKey key = createKey(foreground, background, fontType, effectColor, effectType, additionalEffects, errorStripeColor);
    return new AttributesFlyweight(key);
  }

  static @NotNull AttributesFlyweight create(@NotNull Element element) throws InvalidDataException {
    Color FOREGROUND = readColor(element, "FOREGROUND");
    Color BACKGROUND = readColor(element, "BACKGROUND");
    Color EFFECT_COLOR = readColor(element, "EFFECT_COLOR");
    Color ERROR_STRIPE_COLOR = readColor(element, "ERROR_STRIPE_COLOR");
    int FONT_TYPE = toFontType(readInt(element, "FONT_TYPE"));
    int EFFECT_TYPE = readInt(element, "EFFECT_TYPE");
    // todo additionalEffects are not serialized yet, we have no user-controlled additional effects
    return create(FOREGROUND, BACKGROUND, FONT_TYPE, EFFECT_COLOR, toEffectType(EFFECT_TYPE), Collections.emptyMap(), ERROR_STRIPE_COLOR);
  }

  static @NotNull AttributesFlyweight create(@NotNull DataInput in) throws IOException {
    Color FOREGROUND = readColor(in);
    Color BACKGROUND = readColor(in);
    int FONT_TYPE = toFontType(readINT(in));
    Color EFFECT_COLOR = readColor(in);
    Color ERROR_STRIPE_COLOR = readColor(in);
    int EFFECT_TYPE = readINT(in);
    return create(FOREGROUND, BACKGROUND, FONT_TYPE, EFFECT_COLOR, toEffectType(EFFECT_TYPE), Collections.emptyMap(), ERROR_STRIPE_COLOR);
  }

  /**
   * Whether the alpha channel of a text attribute color survives serialization (IJPL-223521).
   * <p>
   * Disabled by default, because enabling it costs more than it gives to a user who does not want transparency:
   * <ul>
   *   <li>an older IDE cannot parse {@code RRGGBBAA}. Color schemes are safe, they are read by
   *   {@code TextAttributesReader}, but a severity or a T_O_D_O attribute saved with alpha fails to load there, which
   *   affects settings sync and downgrades;</li>
   *   <li>the color scheme settings then show every text attribute swatch as eight hex digits, opaque ones included;</li>
   *   <li>a translucent background is composed against the editor canvas only, not against the selection or the caret
   *   row underneath it.</li>
   * </ul>
   * Reading a color with alpha is not gated, so turning the key off does not make already saved data unreadable.
   */
  @ApiStatus.Internal
  public static boolean isTransparencySupported() {
    return Registry.is("editor.text.attributes.transparency", false);
  }

  private final int myHashCode;
  private final Color myForeground;
  private final Color myBackground;
  private final @FontStyle int myFontType;
  private final Color myEffectColor;
  private final EffectType myEffectType;
  private final @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> myAdditionalEffects; // unmodifiable map
  private final Color myErrorStripeColor;

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

  void writeExternal(@NotNull Element element) {
    boolean writeAlpha = isTransparencySupported();
    writeColor(element, "FOREGROUND", getForeground(), writeAlpha);
    writeColor(element, "BACKGROUND", getBackground(), writeAlpha);
    int fontType = getFontType();
    if (fontType != Font.PLAIN) {
      JDOMExternalizerUtil.writeField(element, "FONT_TYPE", String.valueOf(fontType));
    }
    writeColor(element, "EFFECT_COLOR", getEffectColor(), writeAlpha);
    writeColor(element, "ERROR_STRIPE_COLOR", getErrorStripeColor(), writeAlpha);
    int effectType = fromEffectType(getEffectType());
    if (effectType != 0) {
      JDOMExternalizerUtil.writeField(element, "EFFECT_TYPE", String.valueOf(effectType));
    }
    // todo additionalEffects are not serialized yet, we have no user-controlled additional effects
  }

  void writeExternal(@NotNull DataOutput out) throws IOException {
    writeColor(out, getForeground());
    writeColor(out, getBackground());
    writeINT(out, getFontType());
    writeColor(out, getEffectColor());
    writeColor(out, getErrorStripeColor());
    writeINT(out, fromEffectType(getEffectType()));
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

  private static @NotNull FlyweightKey createKey(
    @Nullable Color foreground,
    @Nullable Color background,
    @FontStyle int fontType,
    @Nullable Color effectColor,
    @Nullable EffectType effectType,
    @NotNull Map<@NotNull EffectType, ? extends @NotNull Color> additionalEffects,
    @Nullable Color errorStripeColor
  ) {
    FlyweightKey key = LOCAL_KEY.get();
    if (key == null) {
      key = new FlyweightKey();
      LOCAL_KEY.set(key);
    }
    key.foreground = foreground;
    key.background = background;
    key.fontType = fontType;
    key.effectColor = effectColor;
    key.effectType = effectType;
    key.myAdditionalEffects = additionalEffects.isEmpty() ? Collections.emptyMap() : new EnumMap<>(additionalEffects);
    key.errorStripeColor = errorStripeColor;
    return key;
  }

  private static int readInt(@NotNull Element element, @NotNull @NonNls String fieldName) throws InvalidDataException {
    String value = JDOMExternalizerUtil.readField(element, fieldName, "0");
    try {
      return Integer.parseInt(value);
    }  catch (NumberFormatException e) {
      throw new InvalidDataException(value, e);
    }
  }

  /**
   * A hex string of that length denotes a color with an alpha channel: {@code RRGGBBAA}.
   * Fully opaque colors are stored as an unpadded {@code RRGGBB} number, so they never reach that length.
   */
  private static final int RGBA_HEX_LENGTH = 8;

  private static @Nullable Color readColor(@NotNull Element element, @NotNull @NonNls String fieldName) throws InvalidDataException {
    String value = JDOMExternalizerUtil.readField(element, fieldName);
    if (value == null) {
      return null;
    }
    if (value.length() == RGBA_HEX_LENGTH) {
      Color color = ColorHexUtil.fromHexOrNull(value);
      if (color != null) {
        return color;
      }
    }
    try {
      return new Color(Integer.parseInt(value, 16));
    } catch (NumberFormatException e) {
      throw new InvalidDataException("Wrong color value: " + value, e);
    }
  }

  private static @Nullable Color readColor(@NotNull DataInput in) throws IOException {
    boolean colorExists = in.readBoolean();
    return colorExists ? new Color(readINT(in)) : null;
  }

  private static void writeColor(@NotNull Element element, @NotNull String fieldName, Color color, boolean writeAlpha) {
    if (color != null) {
      int rgb = color.getRGB() & 0xFFFFFF;
      int alpha = color.getAlpha();
      String string = writeAlpha && alpha != 0xFF ? String.format("%06x%02x", rgb, alpha) : Integer.toString(rgb, 16);
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

  /**
   * Returns {@code value} when it is a valid font style, and {@link Font#PLAIN} when it is not.
   */
  private static @FontStyle int toFontType(int value) {
    switch (value) {
      case Font.BOLD: return Font.BOLD;
      case Font.ITALIC: return Font.ITALIC;
      case Font.BOLD | Font.ITALIC: return Font.BOLD | Font.ITALIC;
      default: return Font.PLAIN;
    }
  }

  private static final int EFFECT_BORDER = 0;
  private static final int EFFECT_LINE = 1;
  private static final int EFFECT_WAVE = 2;
  private static final int EFFECT_STRIKEOUT = 3;
  private static final int EFFECT_BOLD_LINE = 4;
  private static final int EFFECT_BOLD_DOTTED_LINE = 5;

  private static int fromEffectType(EffectType effectType) {
    if (effectType == null) {
      return -1;
    }
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

  private static @Nullable EffectType toEffectType(int effectType) {
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

  private static final class FlyweightKey implements Cloneable {
    private Color foreground;
    private Color background;
    private @FontStyle int fontType;
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
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
