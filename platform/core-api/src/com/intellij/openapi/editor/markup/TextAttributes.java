/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.intellij.lang.annotations.JdkConstants;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Defines the visual representation (colors and effects) of text.
 */
public class TextAttributes implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.markup.TextAttributes");

  public static final TextAttributes ERASE_MARKER = new TextAttributes();

  @SuppressWarnings("NullableProblems")
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

  public TextAttributes() {
    this(null, null, null, EffectType.BOXED, Font.PLAIN);
  }

  private TextAttributes(@NotNull AttributesFlyweight attributesFlyweight) {
    myAttrs = attributesFlyweight;
  }

  public TextAttributes(@NotNull Element element) {
    readExternal(element);
  }

  public TextAttributes(@Nullable Color foregroundColor, @Nullable Color backgroundColor, @Nullable Color effectColor, EffectType effectType, @JdkConstants.FontStyle int fontType) {
    setAttributes(foregroundColor, backgroundColor, effectColor, null, effectType, fontType);
  }

  public void copyFrom(@NotNull TextAttributes other) {
    setAttributes(other.getForegroundColor(),
                  other.getBackgroundColor(),
                  other.getEffectColor(),
                  other.getErrorStripeColor(),
                  other.getEffectType(),
                  other.getFontType());
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
      type = Font.PLAIN;
    }
    myAttrs = myAttrs.withFontType(type);
  }

  /** @noinspection MethodDoesntCallSuperMethod*/
  @Override
  public TextAttributes clone() {
    return new TextAttributes(myAttrs);
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

  public void readExternal(@NotNull Element element) {
    myAttrs = AttributesFlyweight.create(element);
  }

  public void writeExternal(Element element) {
    myAttrs.writeExternal(element);
  }

  @Override
  public String toString() {
    return "[" + getForegroundColor() + "," + getBackgroundColor() + "," + getFontType() + "," + getEffectType() + "," +
           getEffectColor() + "," + getErrorStripeColor() + "]";
  }
}
