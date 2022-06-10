// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

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
    updateFromProvider(event, PlatformDataKeys.COPY_PROVIDER, (provider, presentation) -> {
      presentation.setEnabled(provider.isCopyEnabled(event.getDataContext()));
      boolean isEditorPopup = event.getPlace().equals(ActionPlaces.EDITOR_POPUP);
      presentation.setVisible(!isEditorPopup || provider.isCopyVisible(event.getDataContext()));
    });
  }

  static <T> void updateFromProvider(@NotNull AnActionEvent event,
                                             DataKey<T> key,
                                             BiConsumer<T, Presentation> presentationUpdater) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    T provider = key.getData(dataContext);
    if (provider == null) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }
    if (provider instanceof UpdateInBackground && ((UpdateInBackground)provider).isUpdateInBackground() ||
        EDT.isCurrentThreadEdt()) {
      presentationUpdater.accept(provider, presentation);
    }
    else {
      Utils.getOrCreateUpdateSession(event).computeOnEdt(
        "ProviderState#create", () -> {presentationUpdater.accept(provider, presentation); return presentation;});
    }
  }
}
