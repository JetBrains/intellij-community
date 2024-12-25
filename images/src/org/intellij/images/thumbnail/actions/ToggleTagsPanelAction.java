// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.images.thumbnail.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;

public final class ToggleTagsPanelAction extends ToggleAction {
  public static final String TAGS_PANEL_VISIBLE = "tags.panel.visible";
  public static final String TAGS_PANEL_PROPORTION = "tags.panel.proportion";

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    return project != null && PropertiesComponent.getInstance(project).getBoolean(TAGS_PANEL_VISIBLE, false);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    PropertiesComponent.getInstance(e.getProject()).setValue(TAGS_PANEL_VISIBLE, state);
    ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
    assert view != null;
    view.refresh();
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
    e.getPresentation().setEnabledAndVisible(view != null);
    e.getPresentation().setText(isSelected(e) ? IdeBundle.message("action.text.hide.tags.panel") :
                                IdeBundle.message("action.text.show.tags.panel"));
    super.update(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
