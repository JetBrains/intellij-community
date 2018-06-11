// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class ShadowAction {
  private final AnAction myAction;
  private AnAction myCopyFromAction;
  private final JComponent myComponent;

  private String myActionId;

  private Presentation myPresentation;

  private final Disposable parentDisposable;

  private Disposable listenerDisposable;
  private Disposable shortcutSetDisposable;

  public ShadowAction(AnAction action, AnAction copyFromAction, JComponent component, Presentation presentation, @NotNull Disposable parentDisposable) {
    this(action, copyFromAction, component, parentDisposable);
    myPresentation = presentation;
  }

  // force passing parentDisposable to avoid code like new ShadowAction(this, original, c) (without Disposer.register)
  public ShadowAction(AnAction action, AnAction copyFromAction, JComponent component, @NotNull Disposable parentDisposable) {
    myAction = action;
    this.parentDisposable = parentDisposable;

    myCopyFromAction = copyFromAction;
    myComponent = component;
    myActionId = ActionManager.getInstance().getId(myCopyFromAction);

    myAction.getTemplatePresentation().copyFrom(copyFromAction.getTemplatePresentation());

    UiNotifyConnector uiNotify = new UiNotifyConnector(myComponent, new Activatable() {
      @Override
      public void showNotify() {
        _connect();
      }

      @Override
      public void hideNotify() {
        disposeListeners();
      }
    });
    Disposer.register(parentDisposable, uiNotify);
  }

  private void _connect() {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      return;
    }

    if (listenerDisposable == null) {
      listenerDisposable = Disposer.newDisposable();
      Disposer.register(parentDisposable, listenerDisposable);
      application.getMessageBus().connect(listenerDisposable).subscribe(KeymapManagerListener.TOPIC, new KeymapManagerListener() {
        @Override
        public void activeKeymapChanged(@Nullable Keymap keymap) {
          rebound();
        }

        @Override
        public void shortcutChanged(@NotNull Keymap keymap, @NotNull String actionId) {
          if (myActionId == null || actionId.equals(myActionId)) {
            rebound();
          }
        }
      });
    }

    rebound();
  }

  private void disposeListeners() {
    Disposable disposable = listenerDisposable;
    if (disposable != null) {
      listenerDisposable = null;
      Disposer.dispose(disposable);
    }

    disposeShortcutSetListener();
  }

  private void rebound() {
    disposeShortcutSetListener();

    final KeymapManager keymapManager = getKeymapManager();
    if (keymapManager == null) {
      return;
    }

    myActionId = ActionManager.getInstance().getId(myCopyFromAction);
    if (myPresentation == null) {
      myAction.copyFrom(myCopyFromAction);
    }
    else {
      myAction.getTemplatePresentation().copyFrom(myPresentation);
      myAction.copyShortcutFrom(myCopyFromAction);
    }

    if (myActionId == null) {
      return;
    }

    Keymap keymap = keymapManager.getActiveKeymap();
    if (keymap == null) {
      return;
    }

    ShortcutSet shortcutSet = new CustomShortcutSet(keymap.getShortcuts(myActionId));
    shortcutSetDisposable = Disposer.newDisposable();
    Disposer.register(parentDisposable, shortcutSetDisposable);
    myAction.registerCustomShortcutSet(shortcutSet, myComponent, shortcutSetDisposable);
  }

  private void disposeShortcutSetListener() {
    Disposable disposable = shortcutSetDisposable;
    if (disposable != null) {
      shortcutSetDisposable = null;
      Disposer.dispose(disposable);
    }
  }

  @Nullable
  private static KeymapManager getKeymapManager() {
    return ApplicationManager.getApplication().isDisposed() ? null : KeymapManager.getInstance();
  }

  public void reconnect(AnAction copyFromAction) {
    myCopyFromAction = copyFromAction;
    _connect();
  }
}
