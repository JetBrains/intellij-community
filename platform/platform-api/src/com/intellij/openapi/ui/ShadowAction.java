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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class ShadowAction implements Disposable {
  private final AnAction myAction;
  private AnAction myCopyFromAction;
  private final JComponent myComponent;

  private ShortcutSet myShortcutSet;
  private String myActionId;

  private final Keymap.Listener myKeymapListener;
  private Keymap myKeymap;

  private Presentation myPresentation;
  private final UiNotifyConnector myUiNotify;

  private boolean isConnected;
  private boolean isListenerAdded;

  public ShadowAction(AnAction action, AnAction copyFromAction, JComponent component, Presentation presentation) {
    this(action, copyFromAction, component);
    myPresentation = presentation;
  }

  public ShadowAction(AnAction action, AnAction copyFromAction, JComponent component) {
    myAction = action;

    myCopyFromAction = copyFromAction;
    myComponent = component;
    myActionId = ActionManager.getInstance().getId(myCopyFromAction);

    myAction.getTemplatePresentation().copyFrom(copyFromAction.getTemplatePresentation());

    myKeymapListener = new Keymap.Listener() {
      @Override
      public void onShortcutChanged(final String actionId) {
        if (myActionId == null || actionId.equals(myActionId)) {
          rebound();
        }
      }
    };

    myUiNotify = new UiNotifyConnector(myComponent, new Activatable() {
      @Override
      public void showNotify() {
        _connect();
      }

      @Override
      public void hideNotify() {
        disconnect();
      }
    });
  }

  private void _connect() {
    disconnect();

    Application application = ApplicationManager.getApplication();
    if (application == null) {
      return;
    }

    isConnected = true;

    if (!isListenerAdded) {
      application.getMessageBus().connect(this).subscribe(KeymapManagerListener.TOPIC, new KeymapManagerListener() {
        @Override
        public void activeKeymapChanged(@Nullable Keymap keymap) {
          if (isConnected) {
            rebound();
          }
        }
      });
      isListenerAdded = true;
    }

    rebound();
  }

  private void disconnect() {
    isConnected = false;

    if (myKeymap != null) {
      myKeymap.removeShortcutChangeListener(myKeymapListener);
    }
  }

  private void rebound() {
    final KeymapManager mgr = getKeymapManager();
    if (mgr == null) return;

    myActionId = ActionManager.getInstance().getId(myCopyFromAction);
    if (myPresentation == null) {
      myAction.copyFrom(myCopyFromAction);
    } else {
      myAction.getTemplatePresentation().copyFrom(myPresentation);
      myAction.copyShortcutFrom(myCopyFromAction);
    }

    unregisterAll();

    myKeymap = mgr.getActiveKeymap();
    myKeymap.addShortcutChangeListener(myKeymapListener);

    if (myActionId == null) return;

    final Shortcut[] shortcuts = myKeymap.getShortcuts(myActionId);
    myShortcutSet = new CustomShortcutSet(shortcuts);
    myAction.registerCustomShortcutSet(myShortcutSet, myComponent);
  }

  private void unregisterAll() {
    if (myShortcutSet != null) {
      myAction.unregisterCustomShortcutSet(myComponent);
    }

    if (myKeymap != null) {
      myKeymap.removeShortcutChangeListener(myKeymapListener);
    }
  }

  @Override
  public void dispose() {
    unregisterAll();
    Disposer.dispose(myUiNotify);
    disconnect();
  }

  @Nullable
  private static KeymapManager getKeymapManager() {
    return ApplicationManager.getApplication().isDisposed() ? null : KeymapManager.getInstance();
  }

  public void reconnect(AnAction copyFromAction) {
    disconnect();
    myCopyFromAction = copyFromAction;
    _connect();
  }
}
