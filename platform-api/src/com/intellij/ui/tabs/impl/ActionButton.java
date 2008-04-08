package com.intellij.ui.tabs.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

class ActionButton extends IconButton implements ActionListener {

  private InplaceButton myButton;
  private Presentation myPrevPresentation;
  private AnAction myAction;
  private String myPlace;
  private TabInfo myTabInfo;
  private JBTabsImpl myTabs;

  public ActionButton(JBTabsImpl tabs, TabInfo tabInfo, AnAction action, String place) {
    super(null, action.getTemplatePresentation().getIcon());
    myTabs = tabs;
    myTabInfo = tabInfo;
    myAction = action;
    myPlace = place;
    myButton = new InplaceButton(this, this);
    myButton.setVisible(false);
  }

  public InplaceButton getComponent() {
    return myButton;
  }

  public boolean update() {
    AnActionEvent event = createAnEvent(null);

    if (event == null) return false;

    myAction.update(event);
    Presentation p = event.getPresentation();
    boolean changed = !areEqual(p, myPrevPresentation);

    if (changed) {
      myButton.setIcon(p.getIcon());
      String tooltipText = AnAction.createTooltipText(p.getText(), myAction);
      myButton.setToolTipText(tooltipText.length() > 0 ? tooltipText : null);
      myButton.setVisible(p.isEnabled() && p.isVisible());
    }

    myPrevPresentation = p;

    return changed;
  }

  private boolean areEqual(Presentation p1, Presentation p2) {
    if (p1 == null || p2 == null) return false;

    return ComparableObjectCheck.equals(p1.getText(), p2.getText()) &&
           ComparableObjectCheck.equals(p1.getIcon(), p2.getIcon()) &&
           p1.isEnabled() == p2.isEnabled() &&
           p1.isVisible() == p2.isVisible();

  }

  public void actionPerformed(final ActionEvent e) {
    AnActionEvent event = createAnEvent(null);
    if (event != null) {
      myAction.beforeActionPerformedUpdate(event);
      if (event.getPresentation().isEnabled() && event.getPresentation().isVisible()) {
        myAction.actionPerformed(event);
      }
    }
  }

  @Nullable
  private AnActionEvent createAnEvent(InputEvent e) {
    Presentation presentation = (Presentation)myAction.getTemplatePresentation().clone();
    DataContext context = DataManager.getInstance().getDataContext(myTabInfo.getComponent());
    return new AnActionEvent(e, context, myPlace != null ? myPlace : ActionPlaces.UNKNOWN, presentation, myTabs.myActionManager, 0);
  }
}
