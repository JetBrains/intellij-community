package com.intellij.ui.tabs.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.Pass;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

class ActionButton extends IconButton implements ActionListener {

  private InplaceButton myButton;
  private Presentation myPrevPresentation;
  private AnAction myAction;
  private String myPlace;
  private TabInfo myTabInfo;
  private JBTabsImpl myTabs;
  private boolean myAutoHide;
  private boolean myToShow;

  public ActionButton(JBTabsImpl tabs, TabInfo tabInfo, AnAction action, String place, Pass<MouseEvent> pass) {
    super(null, action.getTemplatePresentation().getIcon());
    myTabs = tabs;
    myTabInfo = tabInfo;
    myAction = action;
    myPlace = place;

    myButton = new InplaceButton(this, this, pass);
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

    setIcons(p.getIcon(), p.getDisabledIcon(), p.getHoveredIcon());

    if (changed) {
      myButton.setIcons(this);
      String tooltipText = AnAction.createTooltipText(p.getText(), myAction);
      myButton.setToolTipText(tooltipText.length() > 0 ? tooltipText : null);
      myButton.setVisible(p.isEnabled() && p.isVisible());
    }

    myPrevPresentation = p;

    return changed;
  }


  private boolean areEqual(Presentation p1, Presentation p2) {
    if (p1 == null || p2 == null) return false;

    return ObjectUtils.equals(p1.getText(), p2.getText())
           && ObjectUtils.equals(p1.getIcon(), p2.getIcon())
           && ObjectUtils.equals(p1.getHoveredIcon(), p2.getHoveredIcon())
           && p1.isEnabled() == p2.isEnabled()
           && p1.isVisible() == p2.isVisible();

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

  public void setAutoHide(final boolean autoHide) {
    myAutoHide = autoHide;
    if (!myToShow) {
      toggleShowActions(false);
    }
  }

  public boolean isAutoHide() {
    return myAutoHide;
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
