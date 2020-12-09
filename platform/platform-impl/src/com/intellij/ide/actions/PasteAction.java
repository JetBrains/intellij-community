// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    PasteProvider provider = PlatformDataKeys.PASTE_PROVIDER.getData(dataContext);
    presentation.setEnabled(provider != null && provider.isPastePossible(dataContext));
    if (event.getPlace().equals(ActionPlaces.EDITOR_POPUP) && provider != null) {
      presentation.setVisible(presentation.isEnabled());
    }
    else {
      presentation.setVisible(true);
    }
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