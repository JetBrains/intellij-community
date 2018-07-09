/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.components.ServiceManager;

import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class StackingPopupDispatcher implements IdePopupEventDispatcher {
  
  public abstract boolean isPopupFocused();

  public abstract void onPopupShown(JBPopup popup, boolean inStack);
  public abstract void onPopupHidden(JBPopup popup);

  public static StackingPopupDispatcher getInstance() {
    return ServiceManager.getService(StackingPopupDispatcher.class);
  }


  public abstract void hidePersistentPopups();

  public abstract void restorePersistentPopups();

  public abstract void eventDispatched(AWTEvent event);

  public abstract boolean dispatchKeyEvent(KeyEvent e);

  public abstract boolean requestFocus();

  public abstract boolean close();

  public abstract boolean closeActivePopup();
}
