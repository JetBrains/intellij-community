package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataKeys;

/**
 * @author max
 */
public class StartStopMacroRecordingAction extends AnAction {
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getDataContext().getData(DataConstants.EDITOR) != null);
    e.getPresentation().setText(ActionMacroManager.getInstance().isRecording()
                                ? IdeBundle.message("action.stop.macro.recording")
                                : IdeBundle.message("action.start.macro.recording"));
  }

  public void actionPerformed(AnActionEvent e) {
    if (!ActionMacroManager.getInstance().isRecording() ) {
      final ActionMacroManager manager = ActionMacroManager.getInstance();
      manager.startRecording(IdeBundle.message("macro.noname"));
    }
    else {
      ActionMacroManager.getInstance().stopRecording(DataKeys.PROJECT.getData(e.getDataContext()));
    }
  }
}
