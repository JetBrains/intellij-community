/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.intellij.lang.annotations.JdkConstants;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.Field;

/**
 * Defines the visual representation (colors and effects) of text.
 */
public class TextAttributes implements JDOMExternalizable, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.markup.TextAttributes");

  public static final TextAttributes ERASE_MARKER = new TextAttributes();

  private boolean myEnforcedDefaults = false;

  @NotNull
  private AttributesFlyweight myAttrs;

  /**
   * Merges (layers) the two given text attributes.
   *
   * @param under Text attributes to merge "under".
   * @param above Text attributes to merge "above", overriding settings from "under".
   * @return Merged attributes instance.
   */
  @Contract("!null, !null -> !null")
  public static TextAttributes merge(TextAttributes under, TextAttributes above) {
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

    if (above.getEffectColor() != null){
      attrs.setEffectColor(above.getEffectColor());
      attrs.setEffectType(above.getEffectType());
    }
    return attrs;
  }

  private static class Externalizable implements Cloneable, JDOMExternalizable {
    public Color FOREGROUND = null;
    public Color BACKGROUND = null;

    @JdkConstants.FontStyle
    public int FONT_TYPE = Font.PLAIN;

    public Color EFFECT_COLOR = null;
    public int EFFECT_TYPE = EFFECT_BORDER;
    public Color ERROR_STRIPE_COLOR = null;

    private static final int EFFECT_BORDER = 0;
    private static final int EFFECT_LINE = 1;
    private static final int EFFECT_WAVE = 2;
    private static final int EFFECT_STRIKEOUT = 3;
    private static final int EFFECT_BOLD_LINE = 4;
    private static final int EFFECT_BOLD_DOTTED_LINE = 5;

    @Override
    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
      if (FONT_TYPE < 0 || FONT_TYPE > 3) {
        LOG.info("Wrong font type: " + FONT_TYPE);
        FONT_TYPE = 0;
      }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element, new DefaultJDOMExternalizer.JDOMFilter() {
        @Override
        public boolean isAccept(@NotNull Field field) {
          try {
            if (field.getType().equals(Color.class) && field.get(Externalizable.this) == null) return false;
            if (field.getType().equals(int.class) && field.getInt(Externalizable.this) == 0) return false;
          }
          catch (IllegalAccessException e) {
            LOG.error("Can not access: " + field.getName());
          }
          return true;
        }
      });
    }

    private EffectType getEffectType() {
      switch (EFFECT_TYPE) {
        case EFFECT_BORDER:
          return EffectType.BOXED;
        case EFFECT_BOLD_LINE:
          return EffectType.BOLD_LINE_UNDERSCORE;
        case EFFECT_LINE:
          return EffectType.LINE_UNDERSCORE;
        case EFFECT_STRIKEOUT:
          return EffectType.STRIKEOUT;
        case EFFECT_WAVE:
          return EffectType.WAVE_UNDERSCORE;
        case EFFECT_BOLD_DOTTED_LINE:
          return EffectType.BOLD_DOTTED_LINE;
        default:
          return null;
      }
    }

    private void setEffectType(EffectType effectType) {
      if (effectType == EffectType.BOXED) {
        EFFECT_TYPE = EFFECT_BORDER;
      }
      else if (effectType == EffectType.LINE_UNDERSCORE) {
        EFFECT_TYPE = EFFECT_LINE;
      }
      else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
        EFFECT_TYPE = EFFECT_BOLD_LINE;
      }
      else if (effectType == EffectType.STRIKEOUT) {
        EFFECT_TYPE = EFFECT_STRIKEOUT;
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        EFFECT_TYPE = EFFECT_WAVE;
      }
      else if (effectType == EffectType.BOLD_DOTTED_LINE) {
        EFFECT_TYPE = EFFECT_BOLD_DOTTED_LINE;
      }
      else {
        EFFECT_TYPE = -1;
      }
    }
  }

  public TextAttributes() {
    this(null, null, null, EffectType.BOXED, Font.PLAIN);
  }

  public TextAttributes(Color foregroundColor, Color backgroundColor, Color effectColor, EffectType effectType, @JdkConstants.FontStyle int fontType) {
    setAttributes(foregroundColor, backgroundColor, effectColor, null, effectType, fontType);
  }

  public void setAttributes(Color foregroundColor,
                            Color backgroundColor,
                            Color effectColor,
                            Color errorStripeColor,
                            EffectType effectType,
                            @JdkConstants.FontStyle int fontType) {
    myAttrs = AttributesFlyweight.create(foregroundColor, backgroundColor, fontType, effectColor, effectType, errorStripeColor);
  }

  public boolean isEmpty(){
    return getForegroundColor() == null && getBackgroundColor() == null && getEffectColor() == null && getFontType() == Font.PLAIN;
  }

  public boolean isFallbackEnabled() {
    return isEmpty() && !myEnforcedDefaults;
  }

  public void reset() {
    setForegroundColor(null);
    setBackgroundColor(null);
    setEffectColor(null);
    setFontType(Font.PLAIN);
  }

  @NotNull
  public AttributesFlyweight getFlyweight() {
    return myAttrs;
  }

  @NotNull
  public static TextAttributes fromFlyweight(@NotNull AttributesFlyweight flyweight) {
    TextAttributes f = new TextAttributes();
    f.myAttrs = flyweight;
    return f;
  }

  public Color getForegroundColor() {
    return myAttrs.getForeground();
  }

  public void setForegroundColor(Color color) {
    myAttrs = myAttrs.withForeground(color);
  }

  public Color getBackgroundColor() {
    return myAttrs.getBackground();
  }

  public void setBackgroundColor(Color color) {
    myAttrs = myAttrs.withBackground(color);
  }

  public Color getEffectColor() {
    return myAttrs.getEffectColor();
  }

  public void setEffectColor(Color color) {
    myAttrs = myAttrs.withEffectColor(color);
  }

  public Color getErrorStripeColor() {
    return myAttrs.getErrorStripeColor();
  }

  public void setErrorStripeColor(Color color) {
    myAttrs = myAttrs.withErrorStripeColor(color);
  }

  public EffectType getEffectType() {
    return myAttrs.getEffectType();
  }

  public void setEffectType(EffectType effectType) {
    myAttrs = myAttrs.withEffectType(effectType);
  }

  @JdkConstants.FontStyle
  public int getFontType() {
    return myAttrs.getFontType();
  }

  public void setFontType(@JdkConstants.FontStyle int type) {
    if (type < 0 || type > 3) {
      LOG.error("Wrong font type: " + type);
      type = 0;
    }
    myAttrs = myAttrs.withFontType(type);
  }

  @Override
  public TextAttributes clone() {
    TextAttributes cloned = new TextAttributes();
    cloned.myAttrs = myAttrs;
    cloned.myEnforcedDefaults = myEnforcedDefaults;
    return cloned;
  }

  public boolean equals(Object obj) {
    if(!(obj instanceof TextAttributes)) {
      return false;
    }
    // myAttrs are interned, see com.intellij.openapi.editor.markup.AttributesFlyweight.create()
    return myAttrs == ((TextAttributes)obj).myAttrs;
  }

  public int hashCode() {
    return myAttrs.hashCode();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    Externalizable ext = new Externalizable();
    ext.readExternal(element);
    myAttrs = AttributesFlyweight.create(ext.FOREGROUND, ext.BACKGROUND, ext.FONT_TYPE, ext.EFFECT_COLOR, ext.getEffectType(), ext.ERROR_STRIPE_COLOR);
    if (isEmpty()) myEnforcedDefaults = true;
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    Externalizable ext = new Externalizable();

    ext.FOREGROUND = myAttrs.getForeground();
    ext.BACKGROUND = myAttrs.getBackground();
    ext.FONT_TYPE = myAttrs.getFontType();
    ext.EFFECT_COLOR = myAttrs.getEffectColor();
    ext.ERROR_STRIPE_COLOR = myAttrs.getErrorStripeColor();
    ext.setEffectType(myAttrs.getEffectType());

    ext.writeExternal(element);
  }

  @Override
  public String toString() {
    return "[" + getForegroundColor() + "," + getBackgroundColor() + "," + getFontType() + "," + getEffectType() + "," +
           getEffectColor() + "," + getErrorStripeColor() + "]";
  }
}
