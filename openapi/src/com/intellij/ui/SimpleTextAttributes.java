package com.intellij.ui;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public final class SimpleTextAttributes {
  public static final int STYLE_PLAIN = Font.PLAIN;
  public static final int STYLE_BOLD = Font.BOLD;
  public static final int STYLE_ITALIC = Font.ITALIC;
  public static final int FONT_MASK = STYLE_PLAIN | STYLE_BOLD | STYLE_ITALIC;
  public static final int STYLE_STRIKEOUT = STYLE_ITALIC << 1;
  public static final int STYLE_WAVED = STYLE_STRIKEOUT << 1;

  private final Color myFgColor;
  private final Color myWaveColor;
  private final int myStyle;
  public static final SimpleTextAttributes REGULAR_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, null);
  public static final SimpleTextAttributes REGULAR_BOLD_ATTRIBUTES = new SimpleTextAttributes(STYLE_BOLD, null);
  public static final SimpleTextAttributes ERROR_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, Color.red);
  public static final SimpleTextAttributes GRAYED_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, UIManager.getColor("textInactiveText"));
  public static final SimpleTextAttributes SYNTHETIC_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, Color.BLUE);
  public static final SimpleTextAttributes GRAY_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, Color.GRAY);
  public static final SimpleTextAttributes DARK_TEXT = new SimpleTextAttributes(STYLE_PLAIN, new Color(112, 112, 164));
  public static final SimpleTextAttributes SIMPLE_CELL_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, UIManager.getColor("List.foreground"));
  public static final SimpleTextAttributes SELECTED_SIMPLE_CELL_ATTRIBUTES = new SimpleTextAttributes(STYLE_PLAIN, UIManager.getColor("List.selectionForeground"));
  public static final SimpleTextAttributes EXCLUDED_ATTRIBUTES = new SimpleTextAttributes(STYLE_ITALIC, Color.GRAY);

  /**
   * @param fgColor color of the text fragment. <code>color</code> can be
   * <code>null</code>. In that case <code>SimpleColoredComponent</code> will
   * use its foreground to paint the text fragment.
   */
  public SimpleTextAttributes(int style, Color fgColor) {
    this(style, fgColor, null);
  }

  public SimpleTextAttributes(int style, Color fgColor, Color waveColor){
    if((~(STYLE_PLAIN | STYLE_BOLD | STYLE_ITALIC | STYLE_STRIKEOUT | STYLE_WAVED) & style) != 0){
      throw new IllegalArgumentException("wrong style: "+style);
    }
    myFgColor = fgColor;
    myWaveColor = waveColor;
    myStyle = style;
  }

  /**
   * @return foreground color
   */
  public Color getFgColor(){
    return myFgColor;
  }

  /**
   * @return wave color. The method can retun <code>null</code>. <code>null</code>
   * means that color of wave is the same as foreground color.
   */
  public Color getWaveColor(){
    return myWaveColor;
  }

  public int getStyle(){
    return myStyle;
  }

  /**
   * @return whether text is striked out or not
   */
  public boolean isStrikeout(){
    return (myStyle & STYLE_STRIKEOUT) != 0;
  }

  /**
   * @return whether text is waved or not
   */
  public boolean isWaved(){
    return (myStyle & STYLE_WAVED) != 0;
  }

  public static SimpleTextAttributes fromTextAttributes(TextAttributes attributes) {
    if (attributes == null) return REGULAR_ATTRIBUTES;

    Color foregroundColor = attributes.getForegroundColor();
    if (foregroundColor == null) foregroundColor = REGULAR_ATTRIBUTES.getFgColor();

    int style = attributes.getFontType();
    if (attributes.getEffectColor() != null) {
      EffectType effectType = attributes.getEffectType();
      if (effectType == EffectType.STRIKEOUT){
        style |= STYLE_STRIKEOUT;
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE){
        style |= STYLE_WAVED;
      }
      else{
        // not supported
      }
    }
    return new SimpleTextAttributes(style, foregroundColor, attributes.getEffectColor());
  }

  public TextAttributes toTextAttributes() {
    Color effectColor;
    EffectType effectType;
    if (isWaved()) {
      effectColor = myWaveColor;
      effectType = EffectType.WAVE_UNDERSCORE;
    } else if (isStrikeout()) {
      effectColor = myFgColor;
      effectType = EffectType.STRIKEOUT;
    } else{
      effectColor = null;
      effectType = null;
    }
    return new TextAttributes(myFgColor, null,effectColor, effectType, myStyle & FONT_MASK);
  }
}
