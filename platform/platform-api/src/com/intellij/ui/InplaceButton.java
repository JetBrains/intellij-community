package com.intellij.ui;

import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.Pass;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.CenteredIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.TimedDeadzone;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

public final class InplaceButton extends JComponent implements ActiveComponent {

  private boolean myPainting = true;
  private boolean myActive = true;

  private BaseButtonBehavior myBehavior;

  private CenteredIcon myRegular;
  private CenteredIcon myHovered;
  private CenteredIcon myInactive;

  private int myXTransform = 0;
  private int myYTransform = 0;
  private boolean myFill;

  public InplaceButton(String tooltip, final Icon icon, final ActionListener listener) {
    this(new IconButton(tooltip, icon, icon), listener, null);
  }

  public InplaceButton(String tooltip, final Icon icon, final ActionListener listener, final Pass<MouseEvent> me) {
    this(new IconButton(tooltip, icon, icon), listener, me);
  }

  public InplaceButton(IconButton source, final ActionListener listener) {
    this(source, listener, null);
  }

  public InplaceButton(IconButton source, final ActionListener listener, final Pass<MouseEvent> me) {
    this(source, listener, me, TimedDeadzone.DEFAULT);
  }

  public InplaceButton(IconButton source, final ActionListener listener, final Pass<MouseEvent> me, TimedDeadzone.Length mouseDeadzone) {
    myBehavior = new BaseButtonBehavior(this, mouseDeadzone) {
      protected void execute(final MouseEvent e) {
        listener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "execute"));
      }

      @Override
      protected void pass(final MouseEvent e) {
        if (me != null) {
          me.pass(e);
        }
      }
    };

    setIcons(source);

    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setToolTipText(source.getTooltip());
    setOpaque(false);
  }

  public void setMouseDeadzone(final TimedDeadzone.Length deadZone) {
    myBehavior.setMouseDeadzone(deadZone);
  }


  public void setIcons(IconButton source) {
    setIcons(source.getRegular(), source.getInactive(), source.getHovered());
  }

  public void setIcons(final Icon regular, final Icon inactive, final Icon hovered) {
    int width = Math.max(regular.getIconWidth(), inactive.getIconWidth());
    width = Math.max(width, hovered.getIconWidth());
    int height = Math.max(regular.getIconHeight(), inactive.getIconHeight());
    height = Math.max(height, hovered.getIconHeight());


    setPreferredSize(new Dimension(width, height));

    myRegular = new CenteredIcon(regular, width, height);
    myHovered = new CenteredIcon(hovered, width, height);
    myInactive = new CenteredIcon(inactive, width, height);
  }

  public InplaceButton setFillBg(boolean fill) {
    myFill = fill;
    return this;
  }

  public void setPainting(final boolean active) {
    if (myPainting == active) return;

    myPainting = active;

    repaint();
  }

  public void setActive(final boolean active) {
    myActive = active;
    repaint();
  }

  public void setIcon(final Icon icon) {
    setIcons(icon, icon, icon);
  }

  public JComponent getComponent() {
    return this;
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (!myPainting) return;

    if (myFill) {
      g.setColor(UIUtil.getBgFillColor(this));
      g.fillRect(0, 0, getWidth(), getHeight());
    }

    g.translate(myXTransform, myYTransform);


    if (myBehavior.isHovered()) {
      if (myBehavior.isPressedByMouse()) {
        myHovered.paintIcon(this, g, 1, 1);
      }
      else {
        myHovered.paintIcon(this, g, 0, 0);
      }
    }
    else {
      if (myActive) {
        myRegular.paintIcon(this, g, 0, 0);
      }
      else {
        myInactive.paintIcon(this, g, 0, 0);
      }
    }

    g.translate(0, 0);
  }

  public void setTransform(int x, int y) {
    myXTransform = x;
    myYTransform = y;
  }

}
