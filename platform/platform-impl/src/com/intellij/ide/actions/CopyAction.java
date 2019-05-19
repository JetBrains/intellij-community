// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class CopyAction extends AnAction implements DumbAware {

  public CopyAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    CopyProvider provider = PlatformDataKeys.COPY_PROVIDER.getData(dataContext);
    if (provider == null) {
      return;
    }
    provider.performCopy(dataContext);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    CopyProvider provider = PlatformDataKeys.COPY_PROVIDER.getData(dataContext);
    presentation.setEnabled(provider != null && provider.isCopyEnabled(dataContext));
    if (event.getPlace().equals(ActionPlaces.EDITOR_POPUP) && provider != null) {
      presentation.setVisible(provider.isCopyVisible(dataContext));
    }
    else {
      presentation.setVisible(true);
    }
  }
}
