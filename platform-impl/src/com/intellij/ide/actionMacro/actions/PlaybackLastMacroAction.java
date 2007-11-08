package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;

/**
 * @author max
 */
public class PlaybackLastMacroAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    ActionMacroManager.getInstance().playbackLastMacro();
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(
      !ActionMacroManager.getInstance().isPlaying() &&
      ActionMacroManager.getInstance().hasRecentMacro() && e.getDataContext().getData(DataConstants.EDITOR) != null);
  }
}
