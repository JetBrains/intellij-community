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

import com.intellij.openapi.util.Comparing;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.StripedLockConcurrentHashMap;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class AttributesFlyweight {
  private static final StripedLockConcurrentHashMap<FlyweightKey, AttributesFlyweight> entries = new StripedLockConcurrentHashMap<FlyweightKey, AttributesFlyweight>();
  private static final ThreadLocal<FlyweightKey> ourKey = new ThreadLocal<FlyweightKey>();

  private final int myHashCode;
  private final Color      myForeground;
  private final Color      myBackground;
  @JdkConstants.FontStyle
  private final int        myFontType;
  private final Color      myEffectColor;
  private final EffectType myEffectType;
  private final Color      myErrorStripeColor;

  private static class FlyweightKey implements Cloneable {
    private Color foreground;
    private Color background;
    @JdkConstants.FontStyle
    private int fontType;
    private Color effectColor;
    private EffectType effectType;
    private Color errorStripeColor;

    private FlyweightKey() {
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FlyweightKey)) return false;

      FlyweightKey key = (FlyweightKey)o;

      if (fontType != key.fontType) return false;
      if (background != null ? !background.equals(key.background) : key.background != null) return false;
      if (effectColor != null ? !effectColor.equals(key.effectColor) : key.effectColor != null) return false;
      if (effectType != key.effectType) return false;
      if (errorStripeColor != null ? !errorStripeColor.equals(key.errorStripeColor) : key.errorStripeColor != null) return false;
      if (foreground != null ? !foreground.equals(key.foreground) : key.foreground != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return calcHashCode(foreground, background, fontType, effectColor, effectType, errorStripeColor);
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

  @NotNull
  public static AttributesFlyweight create(Color foreground,
                                           Color background,
                                           @JdkConstants.FontStyle int fontType,
                                           Color effectColor,
                                           EffectType effectType,
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
    key.errorStripeColor = errorStripeColor;

    AttributesFlyweight flyweight = entries.get(key);
    if (flyweight != null) {
      return flyweight;
    }

    AttributesFlyweight newValue = new AttributesFlyweight(foreground, background, fontType, effectColor, effectType, errorStripeColor);
    return ConcurrencyUtil.cacheOrGet(entries, key.clone(), newValue);
  }

  private AttributesFlyweight(Color foreground,
                              Color background,
                              @JdkConstants.FontStyle int fontType,
                              Color effectColor,
                              EffectType effectType,
                              Color errorStripeColor) {
    myForeground = foreground;
    myBackground = background;
    myFontType = fontType;
    myEffectColor = effectColor;
    myEffectType = effectType;
    myErrorStripeColor = errorStripeColor;
    myHashCode = calcHashCode(foreground, background, fontType, effectColor, effectType, errorStripeColor);
  }

  private static int calcHashCode(Color foreground,
                                  Color background,
                                  int fontType,
                                  Color effectColor,
                                  EffectType effectType,
                                  Color errorStripeColor) {
    int result = foreground != null ? foreground.hashCode() : 0;
    result = 31 * result + (background != null ? background.hashCode() : 0);
    result = 31 * result + fontType;
    result = 31 * result + (effectColor != null ? effectColor.hashCode() : 0);
    result = 31 * result + (effectType != null ? effectType.hashCode() : 0);
    result = 31 * result + (errorStripeColor != null ? errorStripeColor.hashCode() : 0);
    return result;
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

  public Color getErrorStripeColor() {
    return myErrorStripeColor;
  }

  @NotNull
  public AttributesFlyweight withForeground(Color foreground) {
    return Comparing.equal(foreground, myForeground) ? this : create(foreground, myBackground, myFontType, myEffectColor, myEffectType, myErrorStripeColor);
  }

  @NotNull
  public AttributesFlyweight withBackground(Color background) {
    return Comparing.equal(background, myBackground) ? this : create(myForeground, background, myFontType, myEffectColor, myEffectType, myErrorStripeColor);
  }

  @NotNull
  public AttributesFlyweight withFontType(@JdkConstants.FontStyle int fontType) {
    return fontType == myFontType ? this : create(myForeground, myBackground, fontType, myEffectColor, myEffectType, myErrorStripeColor);
  }

  @NotNull
  public AttributesFlyweight withEffectColor(Color effectColor) {
    return Comparing.equal(effectColor, myEffectColor) ? this : create(myForeground, myBackground, myFontType, effectColor, myEffectType, myErrorStripeColor);
  }

  @NotNull
  public AttributesFlyweight withEffectType(EffectType effectType) {
    return Comparing.equal(effectType, myEffectType) ? this : create(myForeground, myBackground, myFontType, myEffectColor, effectType, myErrorStripeColor);
  }

  @NotNull
  public AttributesFlyweight withErrorStripeColor(Color stripeColor) {
    return Comparing.equal(stripeColor, myErrorStripeColor) ? this : create(myForeground, myBackground, myFontType, myEffectColor, myEffectType, stripeColor);
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
    return "AttributesFlyweight{myForeground=" + myForeground + ", myBackground=" + myBackground + ", myFontType=" + myFontType +
           ", myEffectColor=" + myEffectColor + ", myEffectType=" + myEffectType + ", myErrorStripeColor=" + myErrorStripeColor + '}';
  }
}
