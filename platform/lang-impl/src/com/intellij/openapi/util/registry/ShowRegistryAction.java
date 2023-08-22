// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.util.registry;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public final class ShowRegistryAction extends AnAction implements DumbAware, LightEditCompatible {

  private RegistryUi myUi;

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myUi == null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myUi = new RegistryUi() {
      @Override
      public void dispose() {
        myUi = null;
      }
    };
    if (myUi.show()) {
      EditorFactory.getInstance().refreshAllEditors();
    }
  }
}