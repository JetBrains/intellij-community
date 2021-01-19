// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class StackingPopupDispatcher implements IdePopupEventDispatcher {

  public abstract boolean isPopupFocused();

  public @Nullable JBPopup getFocusedPopup() {
    return null;
  }

  public abstract void onPopupShown(JBPopup popup, boolean inStack);
  public abstract void onPopupHidden(JBPopup popup);

  public static StackingPopupDispatcher getInstance() {
    return ApplicationManager.getApplication().getService(StackingPopupDispatcher.class);
  }


  public abstract void hidePersistentPopups();

  public abstract void restorePersistentPopups();

  public abstract void eventDispatched(AWTEvent event);

  public abstract boolean dispatchKeyEvent(KeyEvent e);

  @Override
  public abstract boolean requestFocus();

  @Override
  public abstract boolean close();

  public abstract boolean closeActivePopup();
}
