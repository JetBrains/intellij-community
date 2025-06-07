// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Toggle recursive flag.
 */
final class ToggleRecursiveAction extends ToggleAction {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
    return view != null && view.isRecursive();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
    if (view != null) {
      view.setRecursive(state);
    }
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    ThumbnailViewActionUtil.setEnabled(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
