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
package com.intellij.ui;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.BitUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
@SuppressWarnings({"PointlessBitwiseExpression"})
public final class SimpleTextAttributes {

  @MagicConstant(flags = {
    STYLE_PLAIN, STYLE_BOLD, STYLE_ITALIC, STYLE_STRIKEOUT, STYLE_WAVED, STYLE_UNDERLINE,
    STYLE_BOLD_DOTTED_LINE, STYLE_SEARCH_MATCH, STYLE_SMALLER, STYLE_OPAQUE, STYLE_CLICKABLE})
  public @interface StyleAttributeConstant { }

  public static final int STYLE_PLAIN = Font.PLAIN;
  public static final int STYLE_BOLD = Font.BOLD;
  public static final int STYLE_ITALIC = Font.ITALIC;
  public static final int FONT_MASK = STYLE_PLAIN | STYLE_BOLD | STYLE_ITALIC;
  public static final int STYLE_STRIKEOUT = STYLE_ITALIC << 1;
  public static final int STYLE_WAVED = STYLE_STRIKEOUT << 1;
  public static final int STYLE_UNDERLINE = STYLE_WAVED << 1;
  public static final int STYLE_BOLD_DOTTED_LINE = STYLE_UNDERLINE << 1;
  public static final int STYLE_SEARCH_MATCH = STYLE_BOLD_DOTTED_LINE << 1;
  public static final int STYLE_SMALLER = STYLE_SEARCH_MATCH << 1;
  public static final int STYLE_OPAQUE = STYLE_SMALLER << 1;
  public static final int STYLE_CLICKABLE = STYLE_OPAQUE << 1;

  public static final SimpleTextAttributes REGULAR_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, null);
  public static final SimpleTextAttributes REGULAR_BOLD_ATTRIBUTES = new SimpleTextAttributes(STYLE_BOLD, null);
  public static final SimpleTextAttributes REGULAR_ITALIC_ATTRIBUTES = new SimpleTextAttributes(STYLE_ITALIC, null);
  public static final SimpleTextAttributes ERROR_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, JBColor.red);

  public static final SimpleTextAttributes GRAYED_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, UIUtil.getInactiveTextColor());
  public static final SimpleTextAttributes GRAYED_BOLD_ATTRIBUTES = new SimpleTextAttributes(STYLE_BOLD, UIUtil.getInactiveTextColor());
  public static final SimpleTextAttributes GRAYED_ITALIC_ATTRIBUTES = new SimpleTextAttributes(STYLE_ITALIC, UIUtil.getInactiveTextColor());
  public static final SimpleTextAttributes GRAYED_SMALL_ATTRIBUTES = new SimpleTextAttributes(STYLE_SMALLER, UIUtil.getInactiveTextColor());

  public static final SimpleTextAttributes SYNTHETIC_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, JBColor.blue);

  public static final SimpleTextAttributes GRAY_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, Color.GRAY);
  public static final SimpleTextAttributes GRAY_ITALIC_ATTRIBUTES = new SimpleTextAttributes(STYLE_ITALIC, Color.GRAY);

  public static final SimpleTextAttributes DARK_TEXT = new SimpleTextAttributes(STYLE_PLAIN, new Color(112, 112, 164));
  public static final SimpleTextAttributes SIMPLE_CELL_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, new JBColor(Gray._0, Gray._187));
  public static final SimpleTextAttributes SELECTED_SIMPLE_CELL_ATTRIBUTES =
    new SimpleTextAttributes(STYLE_PLAIN, UIUtil.getListSelectionForeground());
  public static final SimpleTextAttributes EXCLUDED_ATTRIBUTES = new SimpleTextAttributes(STYLE_ITALIC, Color.GRAY);

  public static final SimpleTextAttributes LINK_ATTRIBUTES = new SimpleTextAttributes(STYLE_UNDERLINE, JBColor.blue);
  public static final SimpleTextAttributes LINK_BOLD_ATTRIBUTES = new SimpleTextAttributes(STYLE_UNDERLINE | STYLE_BOLD, JBColor.blue);

  private final Color myBgColor;
  private final Color myFgColor;
  private final Color myWaveColor;
  @StyleAttributeConstant
  private final int myStyle;

  /**
   * @param style   style of the text fragment.                                       5
   * @param fgColor color of the text fragment. {@code color} can be
   *                {@code null}. In that case {@code SimpleColoredComponent} will
   *                use its foreground to paint the text fragment.
   */
  public SimpleTextAttributes(@StyleAttributeConstant int style, Color fgColor) {
    this(style, fgColor, null);
  }

  public SimpleTextAttributes(@StyleAttributeConstant int style, Color fgColor, @Nullable Color waveColor) {
    this(null, fgColor, waveColor, style);
  }

  public SimpleTextAttributes(@Nullable final Color bgColor, final Color fgColor, @Nullable final Color waveColor, @StyleAttributeConstant int style) {
    if ((~(STYLE_PLAIN |
           STYLE_BOLD |
           STYLE_ITALIC |
           STYLE_STRIKEOUT |
           STYLE_WAVED |
           STYLE_UNDERLINE |
           STYLE_BOLD_DOTTED_LINE |
           STYLE_SEARCH_MATCH |
           STYLE_SMALLER |
           STYLE_OPAQUE |
           STYLE_CLICKABLE) & style) != 0) {
      throw new IllegalArgumentException("Wrong style: " + style);
    }

    myBgColor = bgColor;
    myFgColor = fgColor;
    myWaveColor = waveColor;
    myStyle = style;
  }

  /**
   * @return foreground color
   */
  public Color getFgColor() {
    return myFgColor;
  }


  /**
   * @return background color
   */
  @Nullable
  public Color getBgColor() {
    return myBgColor;
  }

  /**
   * @return wave color. The method can return {@code null}. {@code null}
   *         means that color of wave is the same as foreground color.
   */
  @Nullable
  public Color getWaveColor() {
    return myWaveColor;
  }

  @StyleAttributeConstant
  public int getStyle() {
    return myStyle;
  }

  /**
   * @return whether text is struck out or not
   */
  public boolean isStrikeout() {
    return BitUtil.isSet(myStyle, STYLE_STRIKEOUT);
  }

  /**
   * @return whether text is waved or not
   */
  public boolean isWaved() {
    return BitUtil.isSet(myStyle, STYLE_WAVED);
  }

  public boolean isUnderline() {
    return BitUtil.isSet(myStyle, STYLE_UNDERLINE);
  }

  public boolean isBoldDottedLine() {
    return BitUtil.isSet(myStyle, STYLE_BOLD_DOTTED_LINE);
  }

  public boolean isSearchMatch() {
    return BitUtil.isSet(myStyle, STYLE_SEARCH_MATCH);
  }
  
  public boolean isSmaller() {
    return BitUtil.isSet(myStyle, STYLE_SMALLER);
  }

  public boolean isOpaque() {
    return BitUtil.isSet(myStyle, STYLE_OPAQUE);
  }

  public boolean isClickable() {
    return BitUtil.isSet(myStyle, STYLE_CLICKABLE);
  }

  @NotNull
  public static SimpleTextAttributes fromTextAttributes(TextAttributes attributes) {
    if (attributes == null) return REGULAR_ATTRIBUTES;

    Color fgColor = attributes.getForegroundColor();
    if (fgColor == null) fgColor = REGULAR_ATTRIBUTES.getFgColor();
    Color bgColor = attributes.getBackgroundColor();

    int style = attributes.getFontType();
    if (attributes.getEffectColor() != null) {
      EffectType effectType = attributes.getEffectType();
      if (effectType == EffectType.STRIKEOUT) {
        style |= STYLE_STRIKEOUT;
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        style |= STYLE_WAVED;
      }
      else if (effectType == EffectType.LINE_UNDERSCORE ||
               effectType == EffectType.BOLD_LINE_UNDERSCORE ||
               effectType == EffectType.BOLD_DOTTED_LINE) {
        style |= STYLE_UNDERLINE;
      }
      else if (effectType == EffectType.SEARCH_MATCH) {
        style |= STYLE_SEARCH_MATCH;
      }
      else {
        // not supported
      }
    }
    //noinspection MagicConstant
    return new SimpleTextAttributes(bgColor, fgColor, attributes.getEffectColor(), style);
  }

  @JdkConstants.FontStyle
  public int getFontStyle() {
    return myStyle & FONT_MASK;
  }

  public TextAttributes toTextAttributes() {
    Color effectColor;
    EffectType effectType;
    if (isWaved()) {
      effectColor = myWaveColor;
      effectType = EffectType.WAVE_UNDERSCORE;
    }
    else if (isStrikeout()) {
      effectColor = myWaveColor;
      effectType = EffectType.STRIKEOUT;
    }
    else if (isUnderline()) {
      effectColor = myWaveColor;
      effectType = EffectType.LINE_UNDERSCORE;
    }
    else if (isBoldDottedLine()) {
      effectColor = myWaveColor;
      effectType = EffectType.BOLD_DOTTED_LINE;
    }
    else if (isSearchMatch()) {
      effectColor = myWaveColor;
      effectType = EffectType.SEARCH_MATCH;
    }
    else {
      effectColor = null;
      effectType = null;
    }
    return new TextAttributes(myFgColor, null, effectColor, effectType, myStyle & FONT_MASK);
  }

  public SimpleTextAttributes derive(@StyleAttributeConstant int style, @Nullable Color fg, @Nullable Color bg, @Nullable Color wave) {
    return new SimpleTextAttributes(bg != null ? bg : getBgColor(), fg != null ? fg : getFgColor(), wave != null ? wave : getWaveColor(),
                                    style == -1 ? getStyle() : style);
  }

  // take what differs from REGULAR
  public static SimpleTextAttributes merge(final SimpleTextAttributes weak, final SimpleTextAttributes strong) {
    final int style = strong.getStyle() | weak.getStyle();

    final Color wave;
    if (!Comparing.equal(strong.getWaveColor(), REGULAR_ATTRIBUTES.getWaveColor())) {
      wave = strong.getWaveColor();
    }
    else {
      wave = weak.getWaveColor();
    }
    final Color fg;
    if (!Comparing.equal(strong.getFgColor(), REGULAR_ATTRIBUTES.getFgColor())) {
      fg = strong.getFgColor();
    }
    else {
      fg = weak.getFgColor();
    }
    final Color bg;
    if (!Comparing.equal(strong.getBgColor(), REGULAR_ATTRIBUTES.getBgColor())) {
      bg = strong.getBgColor();
    }
    else {
      bg = weak.getBgColor();
    }

    return new SimpleTextAttributes(bg, fg, wave, style);
  }
}
