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

import java.awt.Component;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.plaf.FontUIResource;

/**
 * @author Sergey.Malenkov
 */
public final class RelativeFont implements PropertyChangeListener {
  private static final String PROPERTY = "font";

  public static final RelativeFont PLAIN = new RelativeFont(Font.PLAIN, 0);
  public static final RelativeFont BOLD = new RelativeFont(Font.BOLD, 0);
  public static final RelativeFont LARGE = new RelativeFont(1f);
  public static final RelativeFont SMALL = new RelativeFont(-1f);
  public static final RelativeFont HUGE = new RelativeFont(2f);
  public static final RelativeFont TINY = new RelativeFont(-2f);

  private final int myStyle;
  private final float mySize;

  public RelativeFont(float size) {
    this.myStyle = -1;
    this.mySize = size;
  }

  public RelativeFont(int style, float size) {
    this.myStyle = style & (Font.BOLD | Font.ITALIC);
    this.mySize = size;
    if (style != myStyle) {
      throw new IllegalArgumentException("style");
    }
  }

  public <T extends Component> T install(T component) {
    Font font = derive(component.getFont());
    if (font != null) {
      component.setFont(new MyFont(font));
    }
    component.addPropertyChangeListener(PROPERTY, this);
    return component;
  }

  public Font derive(Font font) {
    if (font != null) {
      if (-1 != myStyle && myStyle != font.getStyle()) {
        return mySize != 0
               ? font.deriveFont(myStyle, mySize + font.getSize2D())
               : font.deriveFont(myStyle);
      }
      if (mySize != 0) {
        return font.deriveFont(mySize + font.getSize2D());
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
