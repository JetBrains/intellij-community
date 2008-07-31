package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.FocusCommand;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class IdeFocusManagerHeadless extends IdeFocusManager {

  public static final IdeFocusManagerHeadless INSTANCE = new IdeFocusManagerHeadless();

  @NotNull
  public ActionCallback requestFocus(@NotNull final Component c, final boolean forced) {
    return new ActionCallback.Done();
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull final FocusCommand command, final boolean forced) {
    return new ActionCallback.Done();
  }

  public JComponent getFocusTargetFor(@NotNull final JComponent comp) {
    return null;
  }

  public void doWhenFocusSettlesDown(@NotNull final Runnable runnable) {
    runnable.run();
  }

  public Component getFocusedDescendantFor(final Component c) {
    return null;
  }
}
