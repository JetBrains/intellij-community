// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorHexUtil;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.io.DataInputOutputUtil.readINT;
import static com.intellij.util.io.DataInputOutputUtil.writeINT;

/**
 * Reads and writes an {@link AttributesFlyweight} as a JDOM element and as binary data.
 */
@ApiStatus.Internal
public final class AttributesFlyweightSerDe {
  private AttributesFlyweightSerDe() {
  }

  static @NotNull AttributesFlyweight create(@NotNull Element element) throws InvalidDataException {
    Color FOREGROUND = readColor(element, "FOREGROUND");
    Color BACKGROUND = readColor(element, "BACKGROUND");
    Color EFFECT_COLOR = readColor(element, "EFFECT_COLOR");
    Color ERROR_STRIPE_COLOR = readColor(element, "ERROR_STRIPE_COLOR");
    int FONT_TYPE = toFontType(readInt(element, "FONT_TYPE"));
    int EFFECT_TYPE = readInt(element, "EFFECT_TYPE");
    FontFamilies FONT_FAMILIES = readFontFamilies(element);
    // todo additionalEffects are not serialized yet, we have no user-controlled additional effects
    return AttributesFlyweight.create(
      FOREGROUND, BACKGROUND, FONT_TYPE, EFFECT_COLOR, toEffectType(EFFECT_TYPE), Collections.emptyMap(), FONT_FAMILIES, ERROR_STRIPE_COLOR
    );
  }

  static @NotNull AttributesFlyweight create(@NotNull DataInput in) throws IOException {
    Color FOREGROUND = readColor(in);
    Color BACKGROUND = readColor(in);
    int FONT_TYPE = toFontType(readINT(in));
    Color EFFECT_COLOR = readColor(in);
    Color ERROR_STRIPE_COLOR = readColor(in);
    int EFFECT_TYPE = readINT(in);
    FontFamilies FONT_FAMILIES = readFontFamilies(in);
    return AttributesFlyweight.create(
      FOREGROUND, BACKGROUND, FONT_TYPE, EFFECT_COLOR, toEffectType(EFFECT_TYPE), Collections.emptyMap(), FONT_FAMILIES, ERROR_STRIPE_COLOR
    );
  }

  static void writeExternal(@NotNull AttributesFlyweight flyweight, @NotNull Element element) {
    boolean writeAlpha = isTransparencySupported();
    writeColor(element, "FOREGROUND", flyweight.getForeground(), writeAlpha);
    writeColor(element, "BACKGROUND", flyweight.getBackground(), writeAlpha);
    int fontType = flyweight.getFontType();
    if (fontType != Font.PLAIN) {
      JDOMExternalizerUtil.writeField(element, "FONT_TYPE", String.valueOf(fontType));
    }
    writeColor(element, "EFFECT_COLOR", flyweight.getEffectColor(), writeAlpha);
    writeColor(element, "ERROR_STRIPE_COLOR", flyweight.getErrorStripeColor(), writeAlpha);
    int effectType = fromEffectType(flyweight.getEffectType());
    if (effectType != 0) {
      JDOMExternalizerUtil.writeField(element, "EFFECT_TYPE", String.valueOf(effectType));
    }
    FontFamilies fontFamilies = flyweight.getFontFamilies();
    if (fontFamilies != null) {
      JDOMExternalizerUtil.writeField(element, "FONT_FAMILIES", String.join(",", fontFamilies.names()));
    }
    // todo additionalEffects are not serialized yet, we have no user-controlled additional effects
  }

  static void writeExternal(@NotNull AttributesFlyweight flyweight, @NotNull DataOutput out) throws IOException {
    writeColor(out, flyweight.getForeground());
    writeColor(out, flyweight.getBackground());
    writeINT(out, flyweight.getFontType());
    writeColor(out, flyweight.getEffectColor());
    writeColor(out, flyweight.getErrorStripeColor());
    writeINT(out, fromEffectType(flyweight.getEffectType()));
    writeFontFamilies(out, flyweight.getFontFamilies());
  }

  /**
   * Parses the value of the {@code FONT_FAMILIES} option: family names separated by commas.
   *
   * @throws IllegalArgumentException if a name is not valid for {@link FontFamilies#of(List)}
   */
  public static @NotNull FontFamilies parseFontFamilies(@NotNull String value) {
    String[] names = value.split(",", -1);
    List<String> trimmedNames = new ArrayList<>(names.length);
    for (String name : names) {
      trimmedNames.add(name.trim());
    }
    return FontFamilies.of(trimmedNames);
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
  public static boolean isTransparencySupported() {
    return Registry.is("editor.text.attributes.transparency", false);
  }

  private static int readInt(@NotNull Element element, @NotNull @NonNls String fieldName) throws InvalidDataException {
    String value = JDOMExternalizerUtil.readField(element, fieldName, "0");
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
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

  private static @Nullable FontFamilies readFontFamilies(@NotNull Element element) throws InvalidDataException {
    String value = JDOMExternalizerUtil.readField(element, "FONT_FAMILIES");
    if (value == null) {
      return null;
    }
    try {
      return parseFontFamilies(value);
    } catch (IllegalArgumentException e) {
      throw new InvalidDataException("Wrong font families: " + value, e);
    }
  }

  /**
   * Reads the number of names, where zero means no font families, and then the names.
   */
  private static @Nullable FontFamilies readFontFamilies(@NotNull DataInput in) throws IOException {
    int count = readINT(in);
    if (count == 0) {
      return null;
    }
    List<String> names = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      names.add(in.readUTF());
    }
    return FontFamilies.of(names);
  }

  private static void writeFontFamilies(@NotNull DataOutput out, @Nullable FontFamilies fontFamilies) throws IOException {
    if (fontFamilies == null) {
      writeINT(out, 0);
      return;
    }
    List<String> names = fontFamilies.names();
    writeINT(out, names.size());
    for (String name : names) {
      out.writeUTF(name);
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
}
