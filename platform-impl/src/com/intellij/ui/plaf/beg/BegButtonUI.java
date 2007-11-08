package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalButtonUI;
import java.awt.*;

public class BegButtonUI extends MetalButtonUI {
  private final static BegButtonUI begButtonUI = new BegButtonUI();
  private Rectangle viewRect = new Rectangle();
  private Rectangle textRect = new Rectangle();
  private Rectangle iconRect = new Rectangle();

  public static ComponentUI createUI(JComponent c) {
    return begButtonUI;
  }

/*
  protected BasicButtonListener createButtonListener(AbstractButton b) {
    return new BasicButtonListener(b);
  }
*/

  public void paint(Graphics g, JComponent c) {
    AbstractButton b = (AbstractButton)c;
    ButtonModel model = b.getModel();

    FontMetrics fm = g.getFontMetrics();

    Insets i = c.getInsets();

    viewRect.x = i.left;
    viewRect.y = i.top;
    viewRect.width = b.getWidth() - (i.right + viewRect.x);
    viewRect.height = b.getHeight() - (i.bottom + viewRect.y);

    textRect.x = textRect.y = textRect.width = textRect.height = 0;
    iconRect.x = iconRect.y = iconRect.width = iconRect.height = 0;

    Font f = c.getFont();
    g.setFont(f);

    // layout the text and icon
    String text = SwingUtilities.layoutCompoundLabel(
      c, fm, b.getText(), b.getIcon(),
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect,
      b.getText() == null ? 0 : defaultTextIconGap
    );

    clearTextShiftOffset();

    // perform UI specific press action, e.g. Windows L&F shifts text
    if (model.isArmed() && model.isPressed()){
      paintButtonPressed(g, b);
    }

    // Paint the Icon
    if (b.getIcon() != null){
      paintIcon(g, c, iconRect);
    }

    if (text != null && !text.equals("")){
      paintText(g, c, textRect, text);
    }

    if (b.isFocusPainted() && b.hasFocus()){
      // paint UI specific focus
      paintFocus(g, b, viewRect, textRect, iconRect);
    }
  }

  protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
    Rectangle focusRect = new Rectangle();
    String text = b.getText();
    boolean isIcon = b.getIcon() != null;

    // If there is text
    if (text != null && !text.equals("")){
      if (!isIcon){
        focusRect.setBounds(textRect);
      }
      else{
        focusRect.setBounds(iconRect.union(textRect));
      }
    }
    // If there is an icon and no text
    else
      if (isIcon){
        focusRect.setBounds(iconRect);
      }

    g.setColor(getFocusColor());
//    g.drawRect(focusRect.x - 1, focusRect.y - 1, focusRect.width + 1, focusRect.height + 1);
    UIUtil.drawDottedRectangle(g, viewRect.x, viewRect.y, viewRect.x + viewRect.width, viewRect.y + viewRect.height);
  }
}