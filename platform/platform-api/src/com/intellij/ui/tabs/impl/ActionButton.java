// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.TimedDeadzone;

import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

class ActionButton extends IconButton implements ActionListener {
  private final InplaceButton myButton;
  private Presentation myPrevPresentation;
  private final AnAction myAction;
  private final String myPlace;
  private final TabInfo myTabInfo;
  private final JBTabsImpl myTabs;
  private boolean myAutoHide;
  private boolean myToShow;

  ActionButton(JBTabsImpl tabs, TabInfo tabInfo, AnAction action, String place, Consumer<MouseEvent> pass, Consumer<Boolean> hover, TimedDeadzone.Length deadzone) {
    super(null, action.getTemplatePresentation().getIcon());
    myTabs = tabs;
    myTabInfo = tabInfo;
    myAction = action;
    myPlace = place;

    MouseListener myListener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        hover.accept(true);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        hover.accept(false);
      }
    };

    myButton = new InplaceButton(this, this, pass, deadzone) {
      @Override
      protected void doRepaintComponent(Component c) {
        repaintComponent(c);
      }

      @Override
      public void addNotify() {
        super.addNotify();
        myButton.addMouseListener(myListener);
      }

      @Override
      public void removeNotify() {
        super.removeNotify();
        myButton.removeMouseListener(myListener);
      }
    };
    myButton.setVisible(false);
    myButton.setFillBg(false);
  }

  public InplaceButton getComponent() {
    return myButton;
  }

  protected void repaintComponent(Component c) {
    c.repaint();
  }

  public void setMouseDeadZone(TimedDeadzone.Length deadZone) {
    myButton.setMouseDeadzone(deadZone);
  }

  public boolean update() {
    AnActionEvent event = createAnEvent(0);

    myAction.update(event);
    Presentation p = event.getPresentation();
    boolean changed = !areEqual(p, myPrevPresentation);

    setIcons(p.getIcon(), p.getDisabledIcon(), p.getHoveredIcon());

    if (changed) {
      myButton.setIcons(this);
      String tooltipText = KeymapUtil.createTooltipText(p.getText(), myAction);
      myButton.setToolTipText(tooltipText.length() > 0 ? tooltipText : null);
      myButton.setVisible(p.isEnabled() && p.isVisible());
    }

    myPrevPresentation = p;

    return changed;
  }

  private static boolean areEqual(Presentation p1, Presentation p2) {
    if (p1 == null || p2 == null) return false;

    return Comparing.equal(p1.getText(), p2.getText())
           && Comparing.equal(p1.getIcon(), p2.getIcon())
           && Comparing.equal(p1.getHoveredIcon(), p2.getHoveredIcon())
           && p1.isEnabled() == p2.isEnabled()
           && p1.isVisible() == p2.isVisible();
  }

  @Override
  public void actionPerformed(final ActionEvent e) {
    AnActionEvent event = createAnEvent(e.getModifiers());
    if (ActionUtil.lastUpdateAndCheckDumb(myAction, event, true)) {
      ActionUtil.performActionDumbAware(myAction, event);
    }
  }

  private AnActionEvent createAnEvent(int modifiers) {
    Presentation presentation = myAction.getTemplatePresentation().clone();
    DataContext context = DataManager.getInstance().getDataContext(myTabInfo.getComponent());
    return new AnActionEvent(null, context, myPlace != null ? myPlace : ActionPlaces.UNKNOWN, presentation, myTabs.myActionManager, modifiers);
  }

  public void setAutoHide(final boolean autoHide) {
    myAutoHide = autoHide;
    if (!myToShow) {
      toggleShowActions(false);
    }
  }

  public void toggleShowActions(boolean show) {
    if (myAutoHide) {
      myButton.setPainting(show);
    } else {
      myButton.setPainting(true);
    }

    myToShow = show;
  }
}