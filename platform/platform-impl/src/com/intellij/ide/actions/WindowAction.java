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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;

import javax.swing.*;
import java.awt.*;

public abstract class WindowAction extends AnAction {

  public static final String NO_WINDOW_ACTIONS = "no.window.actions";

  protected Window myWindow;
  private static JLabel mySizeHelper = new JLabel("W");

  {
    setEnabledInModalContext(true);
  }

  @Override
  public final void update(AnActionEvent e) {
    Window wnd = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    e.getPresentation().setEnabled(wnd != null && !(wnd instanceof IdeFrame));

    Object noActions = null;
    if (wnd instanceof JDialog) {
      noActions = ((JDialog)wnd).getRootPane().getClientProperty(NO_WINDOW_ACTIONS);
    } else if (wnd instanceof JFrame) {
      noActions = ((JFrame)wnd).getRootPane().getClientProperty(NO_WINDOW_ACTIONS);
    }

    if (noActions != null && "true".equalsIgnoreCase(noActions.toString())) {
      e.getPresentation().setEnabled(false);
    }

    if (e.getPresentation().isEnabled()) {
      myWindow = wnd;
    } else {
      myWindow = null;
    }
  }

  public abstract static class BaseSizeAction extends WindowAction {

    private boolean myHorizontal;
    private boolean myPositive;

    protected BaseSizeAction(boolean horizontal, boolean positive) {
      myHorizontal = horizontal;
      myPositive = positive;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      int baseValue = myHorizontal ? mySizeHelper.getPreferredSize().width : mySizeHelper.getPreferredSize().height;

      int inc = baseValue * (myHorizontal ? Registry.intValue("ide.windowSystem.hScrollChars") : Registry.intValue("ide.windowSystem.vScrollChars"));
      if (!myPositive) {
        inc = -inc;
      }

      Rectangle bounds = myWindow.getBounds();
      if (myHorizontal) {
        bounds.width += inc;
      } else {
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
