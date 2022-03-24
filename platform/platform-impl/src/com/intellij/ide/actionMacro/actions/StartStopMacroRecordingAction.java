// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionMacro.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

final class StartStopMacroRecordingAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isRecording = ActionMacroManager.getInstance().isRecording();

    e.getPresentation().setText(isRecording
                                ? IdeBundle.message("action.stop.macro.recording")
                                : IdeBundle.message("action.start.macro.recording"));

    if (ActionPlaces.STATUS_BAR_PLACE.equals(e.getPlace())) {
      e.getPresentation().setIcon(AllIcons.Actions.Suspend);
    }
    else {
      e.getPresentation().setIcon(null);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!ActionMacroManager.getInstance().isRecording()) {
      final ActionMacroManager manager = ActionMacroManager.getInstance();
      manager.startRecording(e.getProject(), ActionMacroManager.NO_NAME_NAME);
    }
    else {
      ActionMacroManager.getInstance().stopRecording(e.getProject());
    }
  }
}
