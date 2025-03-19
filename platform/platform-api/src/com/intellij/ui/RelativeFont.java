// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static java.util.Locale.ENGLISH;

/**
 * This class is intended to update a component's font automatically,
 * if the default font is changed on L&F update.
 */
public final class RelativeFont implements PropertyChangeListener {
  private static final float MULTIPLIER = 1.09f; // based on the default sizes: 10, 11, 12, 13, 14
  private static final float MINIMUM_FONT_SIZE = 1.0f;

  public static final RelativeFont NORMAL = new RelativeFont(null, null, null);
  public static final RelativeFont PLAIN = NORMAL.style(Font.PLAIN);
  public static final RelativeFont BOLD = NORMAL.style(Font.BOLD);
  public static final RelativeFont ITALIC = NORMAL.style(Font.ITALIC);
  public static final RelativeFont LARGE = NORMAL.large();
  public static final RelativeFont SMALL = NORMAL.small();
  public static final RelativeFont HUGE = LARGE.large();
  public static final RelativeFont TINY = SMALL.small();

  private static final String PROPERTY = "font";

  private final String myFamily;
  private final Integer myStyle;
  private final Float mySize;
  private final float myMinimumSize;

  private RelativeFont(String family, Integer style, Float size, Float minimumSize) {
    myFamily = family;
    myStyle = style;
    mySize = size;
    myMinimumSize = minimumSize;
  }

  private RelativeFont(String family, Integer style, Float size) {
    this(family, style, size, MINIMUM_FONT_SIZE);
  }

  /**
   * @param family a new family to derive font
   * @return a new instance with the specified family
   */
  public RelativeFont family(String family) {
    return family.equals(myFamily) ? this : new RelativeFont(family, myStyle, mySize);
  }

  /**
   * @param style a new style to derive font
   * @return a new instance with the specified style
   */
  public RelativeFont style(int style) {
    return null != myStyle && myStyle == style ? this : new RelativeFont(myFamily, style, mySize);
  }

  /**
   * @return a new instance with increased font size
   */
  public RelativeFont large() {
    float size = mySize == null ? 1f : mySize;
    return new RelativeFont(myFamily, myStyle, size * MULTIPLIER);
  }

  /**
   * @return a new instance with decreased font size
   */
  public RelativeFont small() {
    float size = mySize == null ? 1f : mySize;
    return new RelativeFont(myFamily, myStyle, size / MULTIPLIER);
  }

  /**
   * @return an instance from resource integer that represents number of <code>large</code> (>0) or <code>small</code> (<0) operations
   * over the current instance.
   */
  public @NotNull RelativeFont fromResource(@NonNls @NotNull String propertyName, int defaultOffset) {
    return fromResource(propertyName, defaultOffset, MINIMUM_FONT_SIZE);
  }

  /**
   * @return an instance from resource integer that represents number of <code>large</code> (>0) or <code>small</code> (<0) operations
   * over the current instance. Use custom minimum font size limit.
   */
  public @NotNull RelativeFont fromResource(@NonNls @NotNull String propertyName, int defaultOffset, float minSize) {
    int offset = JBUI.getInt(propertyName, defaultOffset);
    return offset == 0 ? this : scale(offset, minSize);
  }

  /**
   * @see #scale(int, float)
   */
  public @NotNull RelativeFont scale(int offset) {
    return scale(offset, MINIMUM_FONT_SIZE);
  }

  /**
   * Returns an instance that represents larger (>0) or smaller (<0) font over the current instance
   */
  public @NotNull RelativeFont scale(int offset, float minSize) {
    float multiplier = (float)Math.pow(MULTIPLIER, offset);
    return new RelativeFont(myFamily, myStyle, mySize != null ? mySize * multiplier : multiplier, minSize);
  }

  /**
   * Installs this instance on the specified component.
   * It adds the "font" property change listener
   * that replaces a component's font with the relative one.
   *
   * @param component the component to install on
   * @return the same component
   */
  public <T extends Component> T install(T component) {
    Font font = derive(component.getFont());
    if (font != null) {
      component.setFont(new MyFont(font));
    }
    uninstallFrom(component);
    component.addPropertyChangeListener(PROPERTY, this);
    return component;
  }

  /**
   * Uninstalls all instances from the specified component.
   * It just removes all the "font" property change listeners
   * without any font modification.
   *
   * @param component the component to uninstall from
   * @return the same component
   */
  public static <T extends Component> T uninstallFrom(T component) {
    for (PropertyChangeListener listener : component.getPropertyChangeListeners(PROPERTY)) {
      if (listener instanceof RelativeFont) {
        component.removePropertyChangeListener(PROPERTY, listener);
      }
    }
    return component;
  }

  /**
   * Creates a new font by replicating the specified one
   * and applying a new family, style, and/or size.
   *
   * @param font the font to modify
   * @return a new font, or the specified one if a change is not needed
   */
  public Font derive(Font font) {
    if (font == null) return null;

    boolean isSizeConsidered = false;
    boolean isOriginalFontUIResource = font instanceof UIResource;

    if (null != myFamily && !myFamily.equals(font.getFamily(ENGLISH))) {
      int style = null != myStyle ? myStyle : font.getStyle();
      font = new Font(myFamily, style, font.getSize());
    }
    else if (null != myStyle && myStyle != font.getStyle()) {
      isSizeConsidered = true;
      font = mySize != null
             ? font.deriveFont(myStyle, Math.max(mySize * font.getSize2D(), myMinimumSize))
             : font.deriveFont(myStyle);
    }

    if (mySize != null && !isSizeConsidered) {
      font = font.deriveFont(Math.max(mySize * font.getSize2D(), myMinimumSize));
    }

    if (font != null && isOriginalFontUIResource && !(font instanceof UIResource)) {
      font = new FontUIResource(font);
    }

    return font;
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (!(event.getNewValue() instanceof MyFont) && (event.getSource() instanceof Component component) && PROPERTY.equals(event.getPropertyName())) {
      Font font = derive(event.getNewValue() instanceof Font ? (Font)event.getNewValue() : component.getFont());
      if (font != null) {
        component.setFont(new MyFont(font));
      }
    }
  }

  private static final class MyFont extends FontUIResource {
    private MyFont(Font font) {
      super(font);
    }
  }
}
