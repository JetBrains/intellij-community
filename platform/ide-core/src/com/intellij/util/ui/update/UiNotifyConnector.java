// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update;

import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.ref.WeakReference;
import java.util.Objects;

public class UiNotifyConnector implements Disposable, HierarchyListener {
  @NotNull
  private final WeakReference<Component> myComponent;
  private Activatable myTarget;
  private boolean myDeferred = true;

  /**
   * @param sig parameter is used to avoid clash with the deprecated constructor
   */
  protected UiNotifyConnector(@NotNull Component component, @NotNull Activatable target, Void sig) {
    myComponent = new WeakReference<>(component);
    myTarget = target;
  }

  /**
   * @param sig parameter is used to avoid clash with the deprecated constructor
   */
  protected UiNotifyConnector(@NotNull Component component, @NotNull Activatable target, boolean deferred, Void sig) {
    this(component, target, sig);
    myDeferred = deferred;
  }

  public static UiNotifyConnector installOn(@NotNull Component component, @NotNull Activatable target, boolean deferred) {
    UiNotifyConnector connector = new UiNotifyConnector(component, target, deferred, null);
    connector.setupListeners();
    return connector;
  }

  public static UiNotifyConnector installOn(@NotNull Component component, @NotNull Activatable target) {
    UiNotifyConnector connector = new UiNotifyConnector(component, target, null);
    connector.setupListeners();
    return connector;
  }

  /**
   * @deprecated Use the static method {@link UiNotifyConnector#installOn(Component, Activatable, boolean)}.
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link UiNotifyConnector#setupListeners()}
   * method
   */
  @Deprecated
  public UiNotifyConnector(@NotNull Component component, @NotNull Activatable target) {
    myComponent = new WeakReference<>(component);
    myTarget = target;
    setupListeners();
  }

  /**
   * @deprecated Use the static method {@link UiNotifyConnector#installOn(Component, Activatable, boolean)}.
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link UiNotifyConnector#setupListeners()}
   * method
   */
  @Deprecated
  public UiNotifyConnector(@NotNull Component component, @NotNull Activatable target, boolean deferred) {
    this(component, target);
    myDeferred = deferred;
  }

  public void setupListeners() {
    Component component = Objects.requireNonNull(myComponent.get());
    if (ComponentUtil.isShowing(component, false)) {
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

    ContextAwareRunnable runnable = () -> {
      Component c = myComponent.get();
      if (isDisposed() || c == null) {
        return;
      }

      if (UIUtil.isShowing(c, false)) {
        showNotify();
      }
      else {
        hideNotify();
      }
    };

    if (myDeferred) {
      Application app = ApplicationManager.getApplication();
      if (app != null && app.isDispatchThread()) {
        app.invokeLater(runnable, ModalityState.current());
      }
      else {
        SwingUtilities.invokeLater(runnable);
      }
    }
    else {
      runnable.run();
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

    /**
     * Use {@link Once#installOn(Component, Activatable, boolean) method}
     * @param sig parameter is used to avoid clash with the deprecated constructor
     */
    private Once(final Component component, final Activatable target, Void sig) {
      super(component, target);
    }

    @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
    public static Once installOn(final @NotNull Component component, final @NotNull Activatable target) {
      Once once = new Once(component, target, null);
      once.setupListeners();
      return once;
    }

    /**
     * @deprecated Use the static method {@link Once#installOn(Component, Activatable, boolean)}.
     * <p>
     * Also, note that non-deprecated constructor is side effect free, and you should call for {@link Once#setupListeners()}
     * method
     */
    @Deprecated
    public Once(final Component component, final Activatable target) {
      super(component, target);
    }

    @Override
    protected void hideNotify() {
      super.hideNotify();
      myHidden = true;
      disposeIfNeeded();
    }

    @Override
    protected void showNotify() {
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
    doWhenFirstShown(c, runnable, null);
  }

  public static void doWhenFirstShown(@NotNull Component c, @NotNull Runnable runnable) {
    doWhenFirstShown(c, runnable, null);
  }

  public static void doWhenFirstShown(@NotNull Component c, @NotNull Runnable runnable, @Nullable Disposable parent) {
    doWhenFirstShown(c, new Activatable() {
      @Override
      public void showNotify() {
        runnable.run();
      }
    }, parent);
  }

  private static void doWhenFirstShown(@NotNull Component c, @NotNull Activatable activatable, @Nullable Disposable parent) {
    UiNotifyConnector connector = new UiNotifyConnector(c, activatable, null) {
      @Override
      protected void showNotify() {
        super.showNotify();
        Disposer.dispose(this);
      }
    };
    connector.setupListeners();
    if (parent != null) {
      Disposer.register(parent, connector);
    }
  }

  @ApiStatus.Experimental
  public static void forceNotifyIsShown(@NotNull Component c) {
    UIUtil.uiTraverser(c).forEach(child -> {
      if (UIUtil.isShowing(child, false)) {
        for (HierarchyListener listener : child.getHierarchyListeners()) {
          if (listener instanceof UiNotifyConnector notifyConnector &&
              !notifyConnector.isDisposed()) {
            notifyConnector.showNotify();
          }
        }
      }
    });
  }
}
