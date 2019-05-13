/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static java.util.Locale.ENGLISH;

/**
 * This class is intended to update a component's font automatically,
 * if the default font is changed on L&F update.
 *
 * @author Sergey.Malenkov
 */
public final class RelativeFont implements PropertyChangeListener {
  private static final float MULTIPLIER = 1.09f; // based on the default sizes: 10, 11, 12, 13, 14
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

  private RelativeFont(String family, Integer style, Float size) {
    myFamily = family;
    myStyle = style;
    mySize = size;
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
    if (font != null) {
      if (null != myFamily && !myFamily.equals(font.getFamily(ENGLISH))) {
        int style = null != myStyle ? myStyle : font.getStyle();
        font = new Font(myFamily, style, font.getSize());
      }
      else if (null != myStyle && myStyle != font.getStyle()) {
        return mySize != null
               ? font.deriveFont(myStyle, mySize * font.getSize2D())
               : font.deriveFont(myStyle);
      }
      if (mySize != null) {
        return font.deriveFont(mySize * font.getSize2D());
      }
    }
    return font;
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (!(event.getNewValue() instanceof MyFont) && (event.getSource() instanceof Component) && PROPERTY.equals(event.getPropertyName())) {
      Component component = (Component)event.getSource();
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
