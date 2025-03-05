// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewElementSamePlaceAction extends NewElementAction {

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    return e.getData(LangDataKeys.IDE_VIEW) != null;
  }

  @ApiStatus.Internal
  @Override
  protected @NotNull PopupHandler createPopupHandler(@NotNull AnActionEvent e) {
    return new SamePlacePopupHandler(e);
  }

  private class SamePlacePopupHandler extends PopupHandler {
    private SamePlacePopupHandler(@NotNull AnActionEvent e) {
      super(e);
    }

    @Override
    protected @Nullable String getTitle() {
      return IdeBundle.message("title.popup.new.element.same.place");
    }

    @Override
    protected void show(@NotNull ListPopup popup) {
      Project project = event.getData(CommonDataKeys.PROJECT);
      if (project != null) {
        popup.showCenteredInCurrentWindow(project);
      }
      else {
        popup.showInBestPositionFor(event.getDataContext());
      }
    }
  }
}