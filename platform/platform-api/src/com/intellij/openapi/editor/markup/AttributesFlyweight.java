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

/*
 * @author max
 */
package com.intellij.openapi.editor.markup;

import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.StripedLockConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class AttributesFlyweight {
  private final int myHashCode;
  private static final StripedLockConcurrentHashMap<AttributesFlyweight, AttributesFlyweight> entries = new StripedLockConcurrentHashMap<AttributesFlyweight, AttributesFlyweight>();

  @NotNull
  public static AttributesFlyweight create(Color foreground,
                                           Color background,
                                           int fontType,
                                           Color effectColor,
                                           EffectType effectType,
                                           Color errorStripeColor) {
    AttributesFlyweight key = new AttributesFlyweight(foreground, background, fontType, effectColor, effectType, errorStripeColor);
    return ConcurrencyUtil.cacheOrGet(entries, key, key);
  }

  private final Color      myForeground;
  private final Color      myBackground;
  private final int        myFontType;
  private final Color      myEffectColor ;
  private final EffectType myEffectType;
  private final Color      myErrorStripeColor;

  private AttributesFlyweight(Color foreground,
                      Color background,
                      int fontType,
                      Color effectColor,
                      EffectType effectType,
                      Color errorStripeColor) {
    myForeground = foreground;
    myBackground = background;
    myFontType = fontType;
    myEffectColor = effectColor;
    myEffectType = effectType;
    myErrorStripeColor = errorStripeColor;
    myHashCode = calcHashCode();
  }

  private int calcHashCode() {
    int result = myForeground != null ? myForeground.hashCode() : 0;
    result = 31 * result + (myBackground != null ? myBackground.hashCode() : 0);
    result = 31 * result + myFontType;
    result = 31 * result + (myEffectColor != null ? myEffectColor.hashCode() : 0);
    result = 31 * result + (myEffectType != null ? myEffectType.hashCode() : 0);
    result = 31 * result + (myErrorStripeColor != null ? myErrorStripeColor.hashCode() : 0);
    return result;
  }

  public Color getForeground() {
    return myForeground;
  }

  public Color getBackground() {
    return myBackground;
  }

  public int getFontType() {
    return myFontType;
  }

  public Color getEffectColor() {
    return myEffectColor;
  }

  public EffectType getEffectType() {
    return myEffectType;
  }

  public Color getErrorStripeColor() {
    return myErrorStripeColor;
  }

  public AttributesFlyweight withForeground(Color fore) {
    return create(fore, myBackground, myFontType, myEffectColor, myEffectType, myErrorStripeColor);
  }

  public AttributesFlyweight withBackground(Color back) {
    return create(myForeground, back, myFontType, myEffectColor, myEffectType, myErrorStripeColor);
  }

  public AttributesFlyweight withFontType(int fontType) {
    return create(myForeground, myBackground, fontType, myEffectColor, myEffectType, myErrorStripeColor);
  }

  public AttributesFlyweight withEffectColor(Color effectColor) {
    return create(myForeground, myBackground, myFontType, effectColor, myEffectType, myErrorStripeColor);
  }

  public AttributesFlyweight withEffectType(EffectType effectType) {
    return create(myForeground, myBackground, myFontType, myEffectColor, effectType, myErrorStripeColor);
  }

  public AttributesFlyweight withErrorStripeColor(Color stripeColor) {
    return create(myForeground, myBackground, myFontType, myEffectColor, myEffectType, stripeColor);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AttributesFlyweight that = (AttributesFlyweight)o;

    if (myFontType != that.myFontType) return false;
    if (myBackground != null ? !myBackground.equals(that.myBackground) : that.myBackground != null) return false;
    if (myEffectColor != null ? !myEffectColor.equals(that.myEffectColor) : that.myEffectColor != null) return false;
    if (myEffectType != that.myEffectType) return false;
    if (myErrorStripeColor != null ? !myErrorStripeColor.equals(that.myErrorStripeColor) : that.myErrorStripeColor != null) return false;
    if (myForeground != null ? !myForeground.equals(that.myForeground) : that.myForeground != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  @NonNls
  @Override
  public String toString() {
    return "AttributesFlyweight{" +
           "myForeground=" +
           myForeground +
           ", myBackground=" +
           myBackground +
           ", myFontType=" +
           myFontType +
           ", myEffectColor=" +
           myEffectColor +
           ", myEffectType=" +
           myEffectType +
           ", myErrorStripeColor=" +
           myErrorStripeColor +
           '}';
  }
}
