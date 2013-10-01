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
package com.intellij.ide.actionMacro.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;

/**
 * @author max
 */
public class StartStopMacroRecordingAction extends AnAction implements DumbAware {
  public void update(AnActionEvent e) {
    boolean isRecording = ActionMacroManager.getInstance().isRecording();

    e.getPresentation().setText(isRecording
                                ? IdeBundle.message("action.stop.macro.recording")
                                : IdeBundle.message("action.start.macro.recording"));

    if (ActionPlaces.STATUS_BAR_PLACE.equals(e.getPlace())) {
      e.getPresentation().setIcon(AllIcons.Ide.Macro.Recording_stop);
    }
    else {
      e.getPresentation().setIcon(null);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    if (!ActionMacroManager.getInstance().isRecording()) {
      final ActionMacroManager manager = ActionMacroManager.getInstance();
      manager.startRecording(IdeBundle.message("macro.noname"));
    }
    else {
      ActionMacroManager.getInstance().stopRecording(CommonDataKeys.PROJECT.getData(e.getDataContext()));
    }
  }
}
