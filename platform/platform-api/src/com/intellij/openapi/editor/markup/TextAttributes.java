/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.*;
import org.jdom.Element;

import java.awt.*;

public class TextAttributes implements JDOMExternalizable, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.markup.TextAttributes");

  public static final TextAttributes ERASE_MARKER = new TextAttributes();

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

    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
      if (FONT_TYPE < 0 || FONT_TYPE > 3) {
        LOG.info("Wrong font type: " + FONT_TYPE);
        FONT_TYPE = 0;
      }
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
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
      } else if (effectType == EffectType.LINE_UNDERSCORE) {
        EFFECT_TYPE = EFFECT_LINE;
      } else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
        EFFECT_TYPE = EFFECT_BOLD_LINE;
      } else if (effectType == EffectType.STRIKEOUT) {
        EFFECT_TYPE = EFFECT_STRIKEOUT;
      } else if (effectType == EffectType.WAVE_UNDERSCORE) {
        EFFECT_TYPE = EFFECT_WAVE;
      } else if (effectType == EffectType.BOLD_DOTTED_LINE) {
        EFFECT_TYPE = EFFECT_BOLD_DOTTED_LINE;
      } else {
        EFFECT_TYPE = -1;
      }
    }
  }

  private AttributesFlyweight myAttrs;

  public TextAttributes() {
    myAttrs = AttributesFlyweight.create(null, null, Font.PLAIN, null, EffectType.BOXED, null);
  }

  public TextAttributes(Color foregroundColor, Color backgroundColor, Color effectColor, EffectType effectType, int fontType) {
    myAttrs = AttributesFlyweight.create(foregroundColor, backgroundColor, fontType, effectColor, effectType, null);
  }

  public boolean isEmpty(){
    return getForegroundColor() == null && getBackgroundColor() == null && getEffectColor() == null && getFontType() == Font.PLAIN;
  }

  public AttributesFlyweight getFlyweight() {
    return myAttrs;
  }

  public static TextAttributes fromFlyweight(AttributesFlyweight flyweight) {
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

  public int getFontType() {
    return myAttrs.getFontType();
  }

  public void setFontType(int type) {
    if (type < 0 || type > 3) {
      LOG.error("Wrong font type: " + type);
      type = 0;
    }
    myAttrs = myAttrs.withFontType(type);
  }

  public TextAttributes clone() {
    TextAttributes cloned = new TextAttributes();
    cloned.myAttrs = myAttrs;
    return cloned;
  }

  public boolean equals(Object obj) {
    if(!(obj instanceof TextAttributes)) {
      return false;
    }
    TextAttributes textAttributes = (TextAttributes)obj;
    if(!Comparing.equal(textAttributes.getForegroundColor(), getForegroundColor())) {
      return false;
    }
    if(!Comparing.equal(textAttributes.getBackgroundColor(), getBackgroundColor())) {
      return false;
    }
    if(!Comparing.equal(textAttributes.getErrorStripeColor(), getErrorStripeColor())) {
      return false;
    }
    if(!Comparing.equal(textAttributes.getEffectColor(), getEffectColor())) {
      return false;
    }
    if (textAttributes.getEffectType() != getEffectType()) {
      return false;
    }
    if(textAttributes.getFontType() != getFontType()) {
      return false;
    }
    return true;
  }

  public int hashCode() {
    int hashCode = 0;
    if(getForegroundColor() != null) {
      hashCode += getForegroundColor().hashCode();
    }
    if(getBackgroundColor() != null) {
      hashCode += getBackgroundColor().hashCode();
    }
    if(getErrorStripeColor() != null) {
      hashCode += getErrorStripeColor().hashCode();
    }
    if(getEffectColor() != null) {
      hashCode += getEffectColor().hashCode();
    }
    hashCode += getFontType();
    return hashCode;
  }

  public void readExternal(Element element) throws InvalidDataException {
    Externalizable ext = new Externalizable();
    ext.readExternal(element);
    myAttrs = AttributesFlyweight.create(ext.FOREGROUND, 
                                         ext.BACKGROUND,
                                         ext.FONT_TYPE,
                                         ext.EFFECT_COLOR,
                                         ext.getEffectType(),
                                         ext.ERROR_STRIPE_COLOR);
  }

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
    return "[" +
           getForegroundColor() +
           "," +
           getBackgroundColor() +
           "," +
           getFontType() +
           "," +
           getEffectType() +
           "," +
           getEffectColor() +
           "," +
           getErrorStripeColor() +
           "]";
  }
}
