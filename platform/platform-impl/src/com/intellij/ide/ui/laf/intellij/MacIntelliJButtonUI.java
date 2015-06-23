/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJButtonUI extends DarculaButtonUI {
  private static Rectangle viewRect = new Rectangle();
  private static Rectangle textRect = new Rectangle();
  private static Rectangle iconRect = new Rectangle();

  private static final Icon LEFT = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/buttonLeft.png");
  private static final Icon RIGHT = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/buttonRight.png");
  private static final Icon MIDDLE = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/buttonMiddle.png");
  private static final Icon LEFT_SELECTED = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/selectedButtonLeft.png");
  private static final Icon RIGHT_SELECTED = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/selectedButtonRight.png");
  private static final Icon MIDDLE_SELECTED = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/selectedButtonMiddle.png");
  private static final Icon LEFT_FOCUSED = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/focusedButtonLeft.png");
  private static final Icon RIGHT_FOCUSED = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/focusedButtonRight.png");
  private static final Icon MIDDLE_FOCUSED = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/focusedButtonMiddle.png");
  private static final Icon LEFT_SELECTED_FOCUSED = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/focusedSelectedButtonLeft.png");
  private static final Icon RIGHT_SELECTED_FOCUSED = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/focusedSelectedButtonRight.png");
  private static final Icon MIDDLE_SELECTED_FOCUSED = DarculaLaf.loadIcon("/com/intellij/ide/ui/laf/icons/focusedSelectedButtonMiddle.png");

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJButtonUI();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    int w = c.getWidth();
    int h = c.getHeight();
    if (isHelpButton(c)) {
      ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, getButtonColor1(), 0, h, getButtonColor2()));
      int off = JBUI.scale(22);
      int x = (w - off) / 2;
      int y = (h - off) / 2;
      g.fillOval(x, y, off, off);
      AllIcons.Actions.Help.paintIcon(c, g, x + JBUI.scale(3), y + JBUI.scale(3));
    } else {

      AbstractButton b = (AbstractButton) c;


      ButtonModel model = b.getModel();

      String text = layout(b, SwingUtilities2.getFontMetrics(b, g),
                           b.getWidth(), b.getHeight());

      final Border border = c.getBorder();

      //if (b.isFocusPainted() && b.hasFocus()) {
      //  if (border instanceof MacIntelliJBorder) {
      //    border.paintBorder(b, g, 1, 1, b.getWidth()-2, b.getHeight()-4);
      //  }
      //}

      boolean isDefault = b instanceof JButton && ((JButton)b).isDefaultButton();
      boolean isFocused = c.hasFocus();
      //final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      final boolean square = isSquare(c);
      int x = isFocused ? 0 : 2;
      int y = isFocused ? 0 : (h - viewRect.height) / 2;
      Icon icon;
      icon = isDefault ? isFocused ? LEFT_SELECTED_FOCUSED : LEFT_SELECTED
                       : isFocused ? LEFT_FOCUSED : LEFT;
      icon.paintIcon(b, g, x, y);
      x+=icon.getIconWidth();
      int stop = w - (isFocused ? 0 : 2) - (isFocused ? RIGHT_FOCUSED.getIconWidth() : RIGHT.getIconWidth());
      Graphics gg = g.create(0,0,w,h);
      gg.setClip(x, y, stop - x, h);
      icon = isDefault ? isFocused ? MIDDLE_SELECTED_FOCUSED : MIDDLE_SELECTED
                       : isFocused ? MIDDLE_FOCUSED : MIDDLE;
      while (x < stop) {
        icon.paintIcon(b, gg, x, y);
        x+=icon.getIconWidth();
      }
      gg.dispose();
      icon = isDefault ? isFocused ? RIGHT_SELECTED_FOCUSED : RIGHT_SELECTED
                       : isFocused ? RIGHT_FOCUSED : RIGHT;
      icon.paintIcon(b, g, stop, y);
      //config.restore();


      clearTextShiftOffset();

      // perform UI specific press action, e.g. Windows L&F shifts text
      //if (model.isArmed() && model.isPressed()) {
      //  paintButtonPressed(g,b);
      //}

      // Paint the Icon
      if(b.getIcon() != null) {
        paintIcon(g,c,iconRect);
      }

      if (text != null && !text.equals("")){
        View v = (View) c.getClientProperty(BasicHTML.propertyKey);
        if (v != null) {
          v.paint(g, textRect);
        } else {
          paintText(g, b, textRect, text);
        }
      }
    }
  }

  private String layout(AbstractButton b, FontMetrics fm,
                        int width, int height) {
    Insets i = b.getInsets();
    viewRect.x = i.left;
    viewRect.y = i.top;
    viewRect.width = width - (i.right + viewRect.x);
    viewRect.height = height - (i.bottom + viewRect.y);

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
  protected Color getButtonColor1() {
    return super.getButtonColor1();
  }

  @Override
  protected Color getButtonColor2() {
    return super.getButtonColor2();
  }

  @Override
  protected Color getSelectedButtonColor1() {
    return new Color(0x6cb3fa);
  }

  @Override
  protected Color getSelectedButtonColor2() {
    return new Color(0x077eff);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    return new Dimension(size.width + 16, 27);
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return super.getMaximumSize(c);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return super.getMinimumSize(c);
  }
}
