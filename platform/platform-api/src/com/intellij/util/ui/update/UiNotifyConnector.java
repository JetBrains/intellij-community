// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.ref.WeakReference;

public class UiNotifyConnector implements Disposable, HierarchyListener {
  @NotNull
  private final WeakReference<Component> myComponent;
  private Activatable myTarget;

  public UiNotifyConnector(@NotNull Component component, @NotNull Activatable target) {
    myComponent = new WeakReference<>(component);
    myTarget = target;
    if (component.isShowing()) {
      showNotify();
    }
    else {
      hideNotify();
    }
    if (isDisposed()) {
      return;
    }
    component.addHierarchyListener(this);
  }

  @Override
  public void hierarchyChanged(@NotNull HierarchyEvent e) {
    if (isDisposed() || (e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) <= 0) {
      return;
    }

    Runnable runnable = () -> {
      Component c = myComponent.get();
      if (isDisposed() || c == null) {
        return;
      }

      if (c.isShowing()) {
        showNotify();
      }
      else {
        hideNotify();
      }
    };

    Application app = ApplicationManager.getApplication();
    if (app != null && app.isDispatchThread()) {
      app.invokeLater(runnable, ModalityState.current());
    }
    else {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(runnable);
    }
  }

  protected void hideNotify() {
    myTarget.hideNotify();
  }

  protected void showNotify() {
    myTarget.showNotify();
  }

  protected void hideOnDispose() {
    myTarget.hideNotify();
  }

  @Override
  public void dispose() {
    if (isDisposed()) {
      return;
    }

    hideOnDispose();
    Component c = myComponent.get();
    if (c != null) {
      c.removeHierarchyListener(this);
    }

    myTarget = null;
    myComponent.clear();
  }

  private boolean isDisposed() {
    return myTarget == null;
  }

  public static final class Once extends UiNotifyConnector {
    private boolean myShown;
    private boolean myHidden;

    public Once(final Component component, final Activatable target) {
      super(component, target);
    }

    @Override
    protected final void hideNotify() {
      super.hideNotify();
      myHidden = true;
      disposeIfNeeded();
    }

    @Override
    protected final void showNotify() {
      super.showNotify();
      myShown = true;
      disposeIfNeeded();
    }

    @Override
    protected void hideOnDispose() {}

    private void disposeIfNeeded() {
      if (myShown && myHidden) {
        Disposer.dispose(this);
      }
    }
  }

  public static void doWhenFirstShown(@NotNull JComponent c, @NotNull Runnable runnable) {
    doWhenFirstShown((Component)c, runnable);
  }

  public static void doWhenFirstShown(@NotNull Component c, @NotNull Runnable runnable) {
    doWhenFirstShown(c, new Activatable() {
      @Override
      public void showNotify() {
        runnable.run();
      }
    });
  }

  public static void doWhenFirstShown(@NotNull Component c, @NotNull Activatable activatable) {
    new UiNotifyConnector(c, activatable) {
      @Override
      protected void showNotify() {
        super.showNotify();
        Disposer.dispose(this);
      }
    };
  }
}
