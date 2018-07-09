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
package com.intellij.ui.roots;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * @author Eugene Zhuravlev
 * @author 2003
 */
public class ScalableIconComponent extends JComponent {
  private final Icon myIcon;
  private final Icon mySelectedIcon;
  private boolean myIsSelected = false;

  public ScalableIconComponent(Icon icon) {
    this(icon, icon);
  }

  public ScalableIconComponent(Icon icon, Icon selectedIcon) {
    myIcon = icon;
    mySelectedIcon = selectedIcon != null? selectedIcon : icon;
    if (icon != null) {
      final Dimension size = new Dimension(icon.getIconWidth(), icon.getIconHeight());
      this.setPreferredSize(size);
      this.setMinimumSize(size);
    }
  }

  protected void paintComponent(Graphics g) {
    final Icon icon = myIsSelected? mySelectedIcon : myIcon;
    if (icon != null) {
      final Graphics2D g2 = (Graphics2D)g;

      g2.setBackground(getBackground());
      final AffineTransform savedTransform = g2.getTransform();

      g2.scale(((double)getWidth()) / icon.getIconWidth(), ((double)getHeight()) / icon.getIconHeight());
      icon.paintIcon(this, g2, 0, 0);

      g2.setTransform(savedTransform);
    }

    super.paintComponent(g);
  }


  public final void setSelected(boolean isSelected) {
    myIsSelected = isSelected;
    this.revalidate();
    this.repaint();
  }
}
