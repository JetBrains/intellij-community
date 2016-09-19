/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;

import javax.swing.*;
import java.awt.*;

public abstract class WindowAction extends AnAction implements DumbAware {

  public static void setEnabledFor(Window window, boolean enabled) {
    JRootPane root = getRootPane(window);
    if (root != null) root.putClientProperty(NO_WINDOW_ACTIONS, !enabled);
  }

  private static boolean isEnabledFor(Window window) {
    if (window == null || window instanceof IdeFrame) return false;
    JRootPane root = getRootPane(window);
    if (root == null) return true;
    Object property = root.getClientProperty(NO_WINDOW_ACTIONS);
    return property == null || !property.toString().equals("true");
  }

  private static JRootPane getRootPane(Window window) {
    if (window instanceof RootPaneContainer) {
      RootPaneContainer container = (RootPaneContainer)window;
      return container.getRootPane();
    }
    return null;
  }

  public static final String NO_WINDOW_ACTIONS = "no.window.actions";

  protected Window myWindow;
  private static JLabel mySizeHelper = null;

  {
    setEnabledInModalContext(true);
  }

  @Override
  public final void update(AnActionEvent event) {
    Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    boolean enabled = isEnabledFor(window);
    if (enabled) {
      Editor editor = event.getData(CommonDataKeys.EDITOR);
      enabled = editor == null || !editor.getContentComponent().hasFocus();
    }
    event.getPresentation().setEnabled(enabled);
    myWindow = enabled ? window : null;
  }

  public abstract static class BaseSizeAction extends WindowAction {

    private final boolean myHorizontal;
    private final boolean myPositive;

    protected BaseSizeAction(boolean horizontal, boolean positive) {
      myHorizontal = horizontal;
      myPositive = positive;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (mySizeHelper == null) {
        mySizeHelper = new JLabel("W"); // Must be sure to invoke label constructor from EDT thread or it may lead to a deadlock
      }

      int baseValue = myHorizontal ? mySizeHelper.getPreferredSize().width : mySizeHelper.getPreferredSize().height;

      int inc = baseValue *
                Registry.intValue(myHorizontal ? "ide.windowSystem.hScrollChars" : "ide.windowSystem.vScrollChars");
      if (!myPositive) {
        inc = -inc;
      }

      Rectangle bounds = myWindow.getBounds();
      if (myHorizontal) {
        bounds.width += inc;
      }
      else {
        bounds.height += inc;
      }

      myWindow.setBounds(bounds);
    }
  }

  public static class IncrementWidth extends BaseSizeAction {

    public IncrementWidth() {
      super(true, true);
    }
  }

  public static class DecrementWidth extends BaseSizeAction {

    public DecrementWidth() {
      super(true, false);
    }
  }

  public static class IncrementHeight extends BaseSizeAction {
    public IncrementHeight() {
      super(false, true);
    }
  }

  public static class DecrementHeight extends BaseSizeAction {
    public DecrementHeight() {
      super(false, false);
    }
  }
}
