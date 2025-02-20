// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.intellij.lang.annotations.JdkConstants;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * This class is intended to read text attributes from an element,
 * which may have several value elements with different names.
 *
 * @see TextAttributes#TextAttributes(Element)
 */
@ApiStatus.Internal
public final class TextAttributesReader extends ValueElementReader {
  private static final @NonNls String NAME = "name";
  private static final @NonNls String OPTION = "option";
  private static final @NonNls String BACKGROUND = "BACKGROUND";
  private static final @NonNls String FOREGROUND = "FOREGROUND";
  private static final @NonNls String ERROR_STRIPE = "ERROR_STRIPE_COLOR";
  private static final @NonNls String EFFECT_COLOR = "EFFECT_COLOR";
  private static final @NonNls String EFFECT_TYPE = "EFFECT_TYPE";
  private static final @NonNls String FONT_TYPE = "FONT_TYPE";

  /**
   * Reads a value of the specified type from the given element.
   * Adds ability to process text attributes.
   *
   * @param type    the class that defines the result type
   * @param element the value element
   * @param <T>     the result type
   * @return a value or {@code null} if it cannot be read
   */
  @Override
  public <T> T read(Class<T> type, Element element) {
    if (!TextAttributes.class.equals(type)) {
      return super.read(type, element);
    }

    TextAttributes attributes = new TextAttributes();
    if (element != null) {
      attributes.setAttributes(
        readChild(Color.class, element, FOREGROUND),
        readChild(Color.class, element, BACKGROUND),
        readChild(Color.class, element, EFFECT_COLOR),
        readChild(Color.class, element, ERROR_STRIPE),
        Effect.read(this, element),
        FontStyle.read(this, element));
    }
    //noinspection unchecked
    return (T)attributes;
  }

  /**
   * Finds a child element with the specified name
   * and reads a value of the specified type from it.
   *
   * @param type    the class that defines the result type
   * @param element the parent element
   * @param name    the name of a child element
   * @param <T>     the result type
   * @return a value or {@code null} if it cannot be read
   */
  private <T> T readChild(Class<T> type, Element element, String name) {
    for (Element option : element.getChildren(OPTION)) {
      if (name.equals(option.getAttributeValue(NAME))) {
        return read(type, option);
      }
    }
    return null;
  }

  private enum Effect {
    BORDER(EffectType.BOXED),
    LINE(EffectType.LINE_UNDERSCORE),
    WAVE(EffectType.WAVE_UNDERSCORE),
    STRIKEOUT(EffectType.STRIKEOUT),
    BOLD_LINE(EffectType.BOLD_LINE_UNDERSCORE),
    BOLD_DOTTED_LINE(EffectType.BOLD_DOTTED_LINE),
    FADED(EffectType.FADED);

    private final EffectType myType;

    Effect(EffectType type) {
      this.myType = type;
    }

    static EffectType read(TextAttributesReader reader, Element element) {
      Effect effect = reader.readChild(Effect.class, element, EFFECT_TYPE);
      return effect != null ? effect.myType : EffectType.BOXED;
    }
  }

  private enum FontStyle {
    PLAIN(Font.PLAIN),
    BOLD(Font.BOLD),
    ITALIC(Font.ITALIC),
    BOLD_ITALIC(Font.BOLD | Font.ITALIC);

    private final int myStyle;

    FontStyle(int style) {
      this.myStyle = style;
    }

    @JdkConstants.FontStyle
    static int read(TextAttributesReader reader, Element element) {
      FontStyle style = reader.readChild(FontStyle.class, element, FONT_TYPE);
      return style != null ? style.myStyle : Font.PLAIN;
    }
  }
}
