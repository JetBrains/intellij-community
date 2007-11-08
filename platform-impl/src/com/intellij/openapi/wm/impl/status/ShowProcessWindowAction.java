package com.intellij.openapi.wm.impl.status;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class ShowProcessWindowAction extends ToggleAction {

  public ShowProcessWindowAction() {
    super(ActionsBundle.message("action.ShowProcessWindow.text"), ActionsBundle.message("action.ShowProcessWindow.description"), null);
  }


  public boolean isSelected(final AnActionEvent e) {
    final IdeFrameImpl frame = getFrame();
    if (frame == null) return false;
    return frame.getStatusBar().isProcessWindowOpen();
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getFrame() != null);
  }

  @Nullable
  private IdeFrameImpl getFrame() {
    Container window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    while (window != null) {
      if (window instanceof IdeFrameImpl) return (IdeFrameImpl)window;
      window = window.getParent();
    }

    return null;
  }

  public void setSelected(final AnActionEvent e, final boolean state) {
    final IdeFrameImpl frame = getFrame();
    if (frame == null) return;
    frame.getStatusBar().setProcessWindowOpen(state);
  }
}
