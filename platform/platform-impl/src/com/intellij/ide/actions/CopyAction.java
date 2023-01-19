// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class CopyAction extends AnAction implements DumbAware, LightEditCompatible {

  public CopyAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
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
    updateWithProvider(event, event.getData(PlatformDataKeys.COPY_PROVIDER), false, provider -> {
      boolean isEditorPopup = event.getPlace().equals(ActionPlaces.EDITOR_POPUP);
      event.getPresentation().setEnabled(provider.isCopyEnabled(event.getDataContext()));
      event.getPresentation().setVisible(!isEditorPopup || provider.isCopyVisible(event.getDataContext()));
    });
  }

  static <T extends ActionUpdateThreadAware> void updateWithProvider(@NotNull AnActionEvent event,
                                                                     @Nullable T provider,
                                                                     boolean checkDumbAwareness,
                                                                     @NotNull Consumer<T> consumer) {
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (provider == null ||
        (checkDumbAwareness && project != null && DumbService.isDumb(project) && !DumbService.isDumbAware(provider))) {
      event.getPresentation().setEnabled(false);
      event.getPresentation().setVisible(true);
      return;
    }
    ActionUpdateThread updateThread = provider.getActionUpdateThread();
    if (updateThread == ActionUpdateThread.BGT || EDT.isCurrentThreadEdt()) {
      consumer.accept(provider);
    }
    else {
      event.getUpdateSession().compute(provider, "update", updateThread, () -> {
        consumer.accept(provider);
        return null;
      });
    }
  }
}
