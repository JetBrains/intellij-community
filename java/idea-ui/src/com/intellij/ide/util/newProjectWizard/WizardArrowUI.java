/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;

/**
* @author Konstantin Bulenkov
*/
class WizardArrowUI extends BasicButtonUI {
  private final AbstractButton myButton;
  private static Rectangle viewRect = new Rectangle();
  private static Rectangle textRect = new Rectangle();
  private static Rectangle iconRect = new Rectangle();


  public WizardArrowUI(AbstractButton b, boolean valid) {
    myButton = b;
    //b.setIcon(EmptyIcon.create(1));
    b.setOpaque(false);
    b.setBorder(new EmptyBorder(8, 0, 8, 40));
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WizardArrowUI((AbstractButton)c, false);
  }

  @Override
  protected void paintIcon(Graphics g, JComponent c, Rectangle iconRect) {
  }

  @Override
  protected int getTextShiftOffset() {
    return 5;
  }

  private String layout(AbstractButton b, FontMetrics fm,
                        int width, int height) {
    viewRect.setBounds(0, 0, width, height);
    JBInsets.removeFrom(viewRect, b.getInsets());

    textRect.x = textRect.y = textRect.width = textRect.height = 0;
    iconRect.x = iconRect.y = iconRect.width = iconRect.height = 0;

    // layout the text and icon
    return SwingUtilities.layoutCompoundLabel(
      b, fm, b.getText(), b.getIcon(),
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect,
      b.getText() == null ? 0 : b.getIconTextGap());
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    int w = c.getWidth();
    int h = c.getHeight();
    layout(myButton, SwingUtilities2.getFontMetrics(c, g), w, h);

    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    h-=4;
    if (!myButton.isSelected()) {
      w-=15;
    }
    final Path2D.Double path = new GeneralPath.Double();
    path.moveTo(0, 0);
    path.lineTo(w - h / 2, 0);
    path.lineTo(w, h / 2);
    path.lineTo(w-h/2, h);
    path.lineTo(0, h);
    path.lineTo(0, 0);
    g.setColor(myButton.isSelected() ? UIUtil.getListSelectionBackground() : Gray._255.withAlpha(200));
    ((Graphics2D)g).fill(path);
    g.setColor(Gray._0.withAlpha(50));
    ((Graphics2D)g).draw(path);
    config.restore();
    textRect.x = 2;
    textRect.y-=7;
    c.setForeground(UIUtil.getListForeground(myButton.isSelected()));
    GraphicsUtil.setupAntialiasing(g);
    paintText(g, c, textRect, myButton.getText());
  }
}
