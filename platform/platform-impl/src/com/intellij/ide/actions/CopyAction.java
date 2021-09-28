// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;

public class CopyAction extends AnAction implements DumbAware, LightEditCompatible, UpdateInBackground {

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
    if (provider instanceof UpdateInBackground && ((UpdateInBackground)provider).isUpdateInBackground() ||
        EDT.isCurrentThreadEdt()) {
      ProviderState providerState = ProviderState.create(dataContext, isEditorPopup, provider);
      presentation.setEnabled(providerState.isCopyEnabled);
      presentation.setVisible(providerState.isVisible);
      return;
    }
    Presentation updatedPresentation = Utils.getOrCreateUpdateSession(event).presentation(new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {

      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        ProviderState providerState = ProviderState.create(dataContext, isEditorPopup, provider);
        e.getPresentation().setEnabled(providerState.isCopyEnabled);
        e.getPresentation().setVisible(providerState.isVisible);
      }
    });
    presentation.setEnabled(updatedPresentation.isEnabled());
    presentation.setVisible(updatedPresentation.isVisible());
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
