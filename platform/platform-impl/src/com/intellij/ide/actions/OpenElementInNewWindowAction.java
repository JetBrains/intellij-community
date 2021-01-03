// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/**
 * This action is only needed to change default behaviour of Shift+Enter
 * in lists of elements. Removing keyboard shortcut from the action will
 * stop opening elements in a new editor window
 *
 * @author Konstantin Bulenkov
 * @see FileEditorManagerImpl#getOpenMode(java.awt.AWTEvent)
 */
final class OpenElementInNewWindowAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {}

  @Override
  public void update(@NotNull AnActionEvent e) {
    //This is a marker action
    e.getPresentation().setEnabledAndVisible(false);
  }
}
