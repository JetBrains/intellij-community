package com.intellij.ui.tabs;

import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.impl.TitlePanel;

import javax.swing.*;
import java.awt.*;

public abstract class MoreIcon {

  private ActiveIcon myLeft =
    new ActiveIcon(IconLoader.getIcon("/general/comboArrowLeft.png"), IconLoader.getIcon("/general/comboArrowLeftPassive.png"));
  private ActiveIcon myRight =
    new ActiveIcon(IconLoader.getIcon("/general/comboArrowRight.png"), IconLoader.getIcon("/general/comboArrowRightPassive.png"));

  private int myGap = 2;
  private boolean myLeftPainted;
  private boolean myRightPainted;

  public void paintIcon(final Component c, final Graphics g) {
    myLeft.setActive(isActive());
    myRight.setActive(isActive());

    final Rectangle moreRect = getIconRec();

    if (moreRect == null) return;

    int iconY = getIconY(moreRect);
    int iconX = getIconX(moreRect);


    if (myLeftPainted && myRightPainted) {
      myLeft.paintIcon(c, g, iconX, iconY);
      myRight.paintIcon(c, g, iconX + myLeft.getIconWidth() + myGap, iconY);
    }
    else {
      Icon toPaint = myLeftPainted ? myLeft : (myRightPainted ? myRight : null);
      if (toPaint != null) {
        toPaint.paintIcon(c, g, iconX + getIconWidth() / 2 - myGap - 1, iconY);
      }
    }
  }

  protected int getIconX(final Rectangle iconRec) {
    return iconRec.x + iconRec.width / 2 - getIconWidth() / 2;
  }

  protected int getIconY(final Rectangle iconRec) {
    return iconRec.y + iconRec.height / 2 - getIconHeight() / 2;
  }


  protected abstract boolean isActive();

  protected abstract Rectangle getIconRec();

  public void setPaintedIcons(boolean left, boolean right) {
    myLeftPainted = left;
    myRightPainted = right;
  }

  public int getIconWidth() {
    return myLeft.getIconWidth() + myRight.getIconWidth() + myGap;
  }

  public int getIconHeight() {
    return myLeft.getIconHeight();
  }


}
