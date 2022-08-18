// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.PasteProvider;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class PasteAction extends AnAction implements DumbAware, LightEditCompatible {
  public PasteAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    CopyAction.updateWithProvider(event, event.getData(PlatformDataKeys.PASTE_PROVIDER), false, provider -> {
      boolean isEditorPopup = event.getPlace().equals(ActionPlaces.EDITOR_POPUP);
      boolean enabled = provider.isPastePossible(event.getDataContext());
      event.getPresentation().setEnabled(enabled);
      event.getPresentation().setVisible(!isEditorPopup || enabled);
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    PasteProvider provider = PlatformDataKeys.PASTE_PROVIDER.getData(dataContext);
    if (provider != null && provider.isPasteEnabled(dataContext)) {
      provider.performPaste(dataContext);
    }
  }
}