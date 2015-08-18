/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.DefaultMenuLayout;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

/**
 * @author ignatov
 */
public class JBPopupMenu extends JPopupMenu {
  @Override
  public final void updateUI() {
    super.updateUI();
    setLayout(new MyLayout(this));
  }

  @Override
  public void setLayout(LayoutManager mgr) {
    if (!(mgr instanceof MyLayout)) return;
    super.setLayout(mgr);
  }


  @Override
  public void paint(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    super.paint(g);
    LayoutManager layout = getLayout();
    if (layout instanceof MyLayout) {
      ((MyLayout)layout).paintIfNeed(g);
    }
  }

  private static class MyLayout extends DefaultMenuLayout implements ActionListener {
    private JPopupMenu myTarget;
    int myShift = 0;
    int myScrollDirection = 0;
    Timer myTimer;

    public MyLayout(JPopupMenu target) {
      super(target, BoxLayout.PAGE_AXIS);
      myTarget = target;
      myTimer = new Timer(40, this);
      myTarget.addPopupMenuListener(new PopupMenuListener() {
        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
          switchTimer(true);
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
          switchTimer(false);
        }

        @Override
        public void popupMenuCanceled(PopupMenuEvent e) {
          switchTimer(false);
        }
      });
      if (myTarget.isVisible()) {
        switchTimer(true);
      }
    }

    private void switchTimer(boolean on) {
      if (on && !myTimer.isRunning()) {
        myTimer.start();
      }
      if (!on && myTimer.isRunning()) {
        myTimer.stop();
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (!myTarget.isShowing()) return;
      PointerInfo info = MouseInfo.getPointerInfo();
      if (info == null) return;
      Point mouseLocation = info.getLocation();
      Point targetLocation = myTarget.getLocationOnScreen();
      if (mouseLocation.x < targetLocation.x || mouseLocation.x > targetLocation.x + myTarget.getWidth()) {
        return;
      }
      if (Math.abs(mouseLocation.y - targetLocation.y - getMaxHeight()) < 10) {
        myScrollDirection = 1;
      }
      else if (Math.abs(mouseLocation.y - targetLocation.y) < 10) {
        myScrollDirection = -1;
      }
      else {
        myScrollDirection = 0;
      }
      if (myScrollDirection == 0) {
        myTarget.revalidate();
        myTarget.repaint();
        return;
      }

      SwingUtilities.convertPointFromScreen(mouseLocation, myTarget);
      myTarget.dispatchEvent(
        new MouseEvent(myTarget, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, mouseLocation.x, mouseLocation.y, 0, false));

      int newShift = myShift + 5 * myScrollDirection;
      newShift = Math.max(0, Math.min(super.preferredLayoutSize(myTarget).height - getMaxHeight(), newShift));
      if (newShift != myShift) {
        myShift = newShift;
        myTarget.revalidate();
        myTarget.repaint();
      }
    }

    private Color[] dim = new Color[]{
      JBColor.background(),
      ColorUtil.withAlpha(JBColor.background(), .9),
      ColorUtil.withAlpha(JBColor.background(), .8),
      ColorUtil.withAlpha(JBColor.background(), .7),
      ColorUtil.withAlpha(JBColor.background(), .6),
      ColorUtil.withAlpha(JBColor.background(), .5),
      ColorUtil.withAlpha(JBColor.background(), .4),
      ColorUtil.withAlpha(JBColor.background(), .3),
      ColorUtil.withAlpha(JBColor.background(), .2),
      ColorUtil.withAlpha(JBColor.background(), .1),
    };

    public void paintIfNeed(Graphics g) {
      if (myShift > 0) {
        for (int i = 0; i < dim.length; i++) {
          g.setColor(dim[i]);
          g.drawLine(0, i, myTarget.getWidth(), i);
        }
        AllIcons.General.SplitUp.paintIcon(myTarget, g, myTarget.getWidth() / 2 - AllIcons.General.SplitUp.getIconWidth() / 2, 0);
      }
      if (super.preferredLayoutSize(myTarget).height - getMaxHeight() - myShift > 0) {
        for (int i = 0; i < dim.length; i++) {
          g.setColor(dim[i]);
          g.drawLine(0, myTarget.getHeight() - i, myTarget.getWidth(),
                     myTarget.getHeight() - i);
        }
        AllIcons.General.SplitDown.paintIcon(myTarget, g, myTarget.getWidth() / 2 - AllIcons.General.SplitDown.getIconWidth() / 2,
                                             myTarget.getHeight() - AllIcons.General.SplitDown.getIconHeight());
      }
    }

    @Override
    public void layoutContainer(Container target) {
      Insets insets = target.getInsets();
      int width = target.getWidth() - insets.left - insets.right;
      Component[] components = target.getComponents();
      int y = -myShift + insets.top;
      for (Component component : components) {
        int height = component.getPreferredSize().height;
        component.setBounds(insets.left, y, width, height);
        y += height;
      }
    }

    private int getMaxHeight() {
      GraphicsConfiguration configuration = myTarget.getGraphicsConfiguration();
      if (configuration == null && myTarget.getInvoker() != null) {
        configuration = myTarget.getInvoker().getGraphicsConfiguration();
      }
      if (configuration == null) return Short.MAX_VALUE;
      Rectangle screenRectangle = ScreenUtil.getScreenRectangle(configuration);
      return screenRectangle.height;
    }

    @NotNull
    @Override
    public Dimension preferredLayoutSize(Container target) {
      Dimension dimension = super.preferredLayoutSize(target);
      dimension.height = Math.min(getMaxHeight(), dimension.height);
      return dimension;
    }
  }
}
