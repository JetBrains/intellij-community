/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

public class UiNotifyConnector implements Disposable, HierarchyListener{
  private Component myComponent;
  private Activatable myTarget;

  public UiNotifyConnector(final Component component, final Activatable target) {
    myComponent = component;
    myTarget = target;
    if (component.isShowing()) {
      showNotify();
    } else {
      hideNotify();
    }
    component.addHierarchyListener(this);
  }

  public void hierarchyChanged(HierarchyEvent e) {
    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) > 0) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myComponent == null) return;

          if (myComponent.isShowing()) {
            showNotify();
          }
          else {
            hideNotify();
          }
        }
      }, ModalityState.stateForComponent(myComponent));
    }
  }

  protected void hideNotify() {
    myTarget.hideNotify();
  }

  protected void showNotify() {
    myTarget.showNotify();
  }

  public void dispose() {
    if (myTarget == null) return;

    myTarget.hideNotify();
    myComponent.removeHierarchyListener(this);

    myTarget = null;
    myComponent = null;
  }

  public static class Once extends UiNotifyConnector {

    private boolean myShown;
    private boolean myHidden;

    public Once(final Component component, final Activatable target) {
      super(component, target);
    }

    protected final void hideNotify() {
      super.hideNotify();
      myHidden = true;
      disposeIfNeeded();
    }

    protected final void showNotify() {
      super.showNotify();
      myShown = true;
      disposeIfNeeded();
    }

    private void disposeIfNeeded() {
      if (myShown && myHidden) {
        dispose();
      }
    }
  }

}
