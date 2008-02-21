package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class IdeFocusManagerImpl extends IdeFocusManager {

  private ToolWindowManagerImpl myToolWindowManager;

  public IdeFocusManagerImpl(ToolWindowManagerImpl toolWindowManager) {
    myToolWindowManager = toolWindowManager;
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    return myToolWindowManager.requestFocus(c, forced);
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull final ActionCallback.Runnable command, final boolean forced) {
    return myToolWindowManager.requestFocus(command, forced);
  }

  public JComponent getFocusTargetFor(@NotNull final JComponent comp) {
    return myToolWindowManager.getFocusTargetFor(comp);
  }
}
