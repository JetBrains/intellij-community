// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

public class CopyAction extends AnAction implements DumbAware, LightEditCompatible {

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
    if (provider == null) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }
    boolean isEditorPopup = event.getPlace().equals(ActionPlaces.EDITOR_POPUP);
    if (provider instanceof UpdateInBackground && ((UpdateInBackground)provider).isUpdateInBackground()) {
      ReadAction.nonBlocking(() -> ProviderState.create(dataContext, isEditorPopup, provider))
        .coalesceBy(this)
        .finishOnUiThread(ModalityState.current(), providerState -> {
          presentation.setEnabled(providerState.isCopyEnabled);
          presentation.setVisible(providerState.isVisible);
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }
    else {
      ProviderState providerState = ProviderState.create(dataContext, isEditorPopup, provider);
      presentation.setEnabled(providerState.isCopyEnabled);
      presentation.setVisible(providerState.isVisible);
    }
  }

  private static class ProviderState {
    private final boolean isCopyEnabled;
    private final boolean isVisible;

    private ProviderState(boolean enabled, boolean visible) {
      isCopyEnabled = enabled;
      isVisible = visible;
    }

    private static @NotNull ProviderState create(@NotNull DataContext dataContext, boolean isEditorPopup, @NotNull CopyProvider provider) {
      boolean isCopyEnabled = provider.isCopyEnabled(dataContext);
      boolean isVisible = !isEditorPopup || provider.isCopyVisible(dataContext);
      return new ProviderState(isCopyEnabled, isVisible);
    }
  }
}
