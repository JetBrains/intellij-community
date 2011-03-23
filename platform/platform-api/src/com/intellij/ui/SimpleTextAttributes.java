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
package com.intellij.ui;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
@SuppressWarnings({"PointlessBitwiseExpression"})
public final class SimpleTextAttributes {
  public static final int STYLE_PLAIN = Font.PLAIN;
  public static final int STYLE_BOLD = Font.BOLD;
  public static final int STYLE_ITALIC = Font.ITALIC;
  public static final int FONT_MASK = STYLE_PLAIN | STYLE_BOLD | STYLE_ITALIC;
  public static final int STYLE_STRIKEOUT = STYLE_ITALIC << 1;
  public static final int STYLE_WAVED = STYLE_STRIKEOUT << 1;
  public static final int STYLE_UNDERLINE = STYLE_WAVED << 1;
  public static final int STYLE_BOLD_DOTTED_LINE = STYLE_UNDERLINE << 1;
  public static final int STYLE_SEARCH_MATCH = STYLE_BOLD_DOTTED_LINE << 1;

  public static final SimpleTextAttributes REGULAR_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, null);
  public static final SimpleTextAttributes REGULAR_BOLD_ATTRIBUTES = new SimpleTextAttributes(STYLE_BOLD, null);
  public static final SimpleTextAttributes REGULAR_ITALIC_ATTRIBUTES = new SimpleTextAttributes(STYLE_ITALIC, null);
  public static final SimpleTextAttributes ERROR_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, Color.red);

  public static final SimpleTextAttributes GRAYED_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, UIUtil.getInactiveTextColor());
  public static final SimpleTextAttributes GRAYED_BOLD_ATTRIBUTES = new SimpleTextAttributes(STYLE_BOLD, UIUtil.getInactiveTextColor());

  public static final SimpleTextAttributes SYNTHETIC_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, Color.BLUE);
  public static final SimpleTextAttributes GRAY_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, Color.GRAY);
  public static final SimpleTextAttributes GRAY_ITALIC_ATTRIBUTES = new SimpleTextAttributes(STYLE_ITALIC, Color.GRAY);
  public static final SimpleTextAttributes DARK_TEXT = new SimpleTextAttributes(STYLE_PLAIN, new Color(112, 112, 164));
  public static final SimpleTextAttributes SIMPLE_CELL_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, UIUtil.getListForeground());
  public static final SimpleTextAttributes SELECTED_SIMPLE_CELL_ATTRIBUTES =
    new SimpleTextAttributes(STYLE_PLAIN, UIUtil.getListSelectionForeground());
  public static final SimpleTextAttributes EXCLUDED_ATTRIBUTES = new SimpleTextAttributes(STYLE_ITALIC, Color.GRAY);

  public static final SimpleTextAttributes LINK_ATTRIBUTES = new SimpleTextAttributes(STYLE_UNDERLINE, Color.blue);
  public static final SimpleTextAttributes LINK_BOLD_ATTRIBUTES = new SimpleTextAttributes(STYLE_UNDERLINE | STYLE_BOLD, Color.blue);

  private final Color myBgColor;
  private final Color myFgColor;
  private final Color myWaveColor;
  private final int myStyle;

  /**
   * @param style   style of the text fragment.
   * @param fgColor color of the text fragment. <code>color</code> can be
   *                <code>null</code>. In that case <code>SimpleColoredComponent</code> will
   *                use its foreground to paint the text fragment.
   */
  public SimpleTextAttributes(int style, Color fgColor) {
    this(style, fgColor, null);
  }

  public SimpleTextAttributes(int style, Color fgColor, Color waveColor) {
    this(null, fgColor, waveColor, style);
  }

  public SimpleTextAttributes(final Color bgColor, final Color fgColor, final Color waveColor, final int style) {
    if ((~(STYLE_PLAIN |
           STYLE_BOLD |
           STYLE_ITALIC |
           STYLE_STRIKEOUT |
           STYLE_WAVED |
           STYLE_UNDERLINE |
           STYLE_BOLD_DOTTED_LINE |
           STYLE_SEARCH_MATCH) & style) != 0) {
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
  public Color getBgColor() {
    return myBgColor;
  }

  /**
   * @return wave color. The method can return <code>null</code>. <code>null</code>
   *         means that color of wave is the same as foreground color.
   */
  public Color getWaveColor() {
    return myWaveColor;
  }

  public int getStyle() {
    return myStyle;
  }

  /**
   * @return whether text is struck out or not
   */
  public boolean isStrikeout() {
    return (myStyle & STYLE_STRIKEOUT) != 0;
  }

  /**
   * @return whether text is waved or not
   */
  public boolean isWaved() {
    return (myStyle & STYLE_WAVED) != 0;
  }

  public boolean isUnderline() {
    return (myStyle & STYLE_UNDERLINE) != 0;
  }

  public boolean isBoldDottedLine() {
    return (myStyle & STYLE_BOLD_DOTTED_LINE) != 0;
  }

  public boolean isSearchMatch() {
    return (myStyle & STYLE_SEARCH_MATCH) != 0;
  }

  public static SimpleTextAttributes fromTextAttributes(TextAttributes attributes) {
    if (attributes == null) return REGULAR_ATTRIBUTES;

    Color foregroundColor = attributes.getForegroundColor();
    if (foregroundColor == null) foregroundColor = REGULAR_ATTRIBUTES.getFgColor();

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
    return new SimpleTextAttributes(attributes.getBackgroundColor(), foregroundColor, attributes.getEffectColor(), style);
  }

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

  public SimpleTextAttributes derive(int style, @Nullable Color fg, @Nullable Color bg, @Nullable Color wave) {
    return new SimpleTextAttributes(bg != null ? bg : getBgColor(), fg != null ? fg : getFgColor(), wave != null ? wave : getWaveColor(),
                                    style == -1 ? getStyle() : style);
  }

  // take what differs from REGULAR
  public static SimpleTextAttributes merge(final SimpleTextAttributes weak, final SimpleTextAttributes strong) {
    final int style;
    final Color wave;
    final Color fg;
    final Color bg;
    if (strong.getStyle() != REGULAR_ATTRIBUTES.getStyle()) {
      style = strong.getStyle();
    } else {
      style = weak.getStyle();
    }
    if (! Comparing.equal(strong.getWaveColor(), REGULAR_ATTRIBUTES.getWaveColor())) {
      wave = strong.getWaveColor();
    } else {
      wave = weak.getWaveColor();
    }
    if (! Comparing.equal(strong.getFgColor(), REGULAR_ATTRIBUTES.getFgColor())) {
      fg = strong.getFgColor();
    } else {
      fg = weak.getFgColor();
    }
    if (! Comparing.equal(strong.getBgColor(), REGULAR_ATTRIBUTES.getBgColor())) {
      bg = strong.getBgColor();
    } else {
      bg = weak.getBgColor();
    }

    return new SimpleTextAttributes(bg, fg, wave, style);
  }
}
