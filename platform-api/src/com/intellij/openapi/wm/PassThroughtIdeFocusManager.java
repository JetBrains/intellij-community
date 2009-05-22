package com.intellij.openapi.wm;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class PassThroughtIdeFocusManager extends IdeFocusManager {

  private static final PassThroughtIdeFocusManager ourInstance = new PassThroughtIdeFocusManager();

  public static PassThroughtIdeFocusManager getInstance() {
    return ourInstance;
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
    c.requestFocus();
    return new ActionCallback.Done();
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull FocusCommand command, boolean forced) {
    return command.run();
  }

  public JComponent getFocusTargetFor(@NotNull JComponent comp) {
    return comp;
  }

  public void doWhenFocusSettlesDown(@NotNull Runnable runnable) {
    runnable.run();
  }

  public Component getFocusedDescendantFor(Component comp) {
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focused == null) return null;

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) return focused;

    return null;
  }

  public boolean dispatch(KeyEvent e) {
    return false;
  }

  @Override
  public void suspendKeyProcessingUntil(@NotNull ActionCallback done) {
  }

}
