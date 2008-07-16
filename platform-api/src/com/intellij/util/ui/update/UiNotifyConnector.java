/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util.ui.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;

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
    if (isDisposed()) return;

    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) > 0) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (isDisposed() || myComponent == null) return;

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
    if (isDisposed()) return;

    myTarget.hideNotify();
    myComponent.removeHierarchyListener(this);

    myTarget = null;
    myComponent = null;
  }

  private boolean isDisposed() {
    return myTarget == null;
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
        Disposer.dispose(this);
      }
    }
  }

}
