/*
 * @author max
 */
package com.intellij.openapi.editor.markup;

import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class AttributesFlyweight {
  private static final class MyTHashSet extends THashSet<AttributesFlyweight> {
    public int index(final AttributesFlyweight obj) {
      return super.index(obj);
    }

    public AttributesFlyweight get(int index) {
      return (AttributesFlyweight)_set[index];
    }
  }

  private static final MyTHashSet entries = new MyTHashSet();

  @NotNull
  public static AttributesFlyweight create(Color foreground,
                                           Color background,
                                           int fontType,
                                           Color effectColor,
                                           EffectType effectType,
                                           Color errorStripeColor) {
    AttributesFlyweight key = new AttributesFlyweight(foreground, background, fontType, effectColor, effectType, errorStripeColor);
    synchronized (entries) {
      int idx = entries.index(key);
      if (idx >= 0) {
        return entries.get(idx);
      }

      entries.add(key);
      return key;
    }
  }

  private final Color      myForeground;
  private final Color      myBackground;
  private final int        myFontType;
  private final Color      myEffectColor ;
  private final EffectType myEffectType;
  private final Color      myErrorStripeColor;

  AttributesFlyweight(Color foreground,
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
    int result = myForeground != null ? myForeground.hashCode() : 0;
    result = 31 * result + (myBackground != null ? myBackground.hashCode() : 0);
    result = 31 * result + myFontType;
    result = 31 * result + (myEffectColor != null ? myEffectColor.hashCode() : 0);
    result = 31 * result + (myEffectType != null ? myEffectType.hashCode() : 0);
    result = 31 * result + (myErrorStripeColor != null ? myErrorStripeColor.hashCode() : 0);
    return result;
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
