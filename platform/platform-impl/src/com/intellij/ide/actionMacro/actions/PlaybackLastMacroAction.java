// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PlaybackLastMacroAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ActionMacroManager.getInstance().playbackLastMacro();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!ActionMacroManager.getInstance().isPlaying() &&
                                   ActionMacroManager.getInstance().hasRecentMacro());
  }
}
