// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.images.thumbnail.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.images.ImagesBundle;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class FilterByThemeComboBoxAction extends ComboBoxAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }


  @Override
  public void update(@NotNull final AnActionEvent e) {
    Project project = e.getProject();
    ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
    boolean hasApplicableExtension =
      ContainerUtil.and(ThemeFilter.EP_NAME.getExtensionList(), filter -> project != null && filter.isApplicableToProject(project));
    e.getPresentation().setVisible(view != null && hasApplicableExtension);
    ThemeFilter filter = view != null ? view.getFilter() : null;
    e.getPresentation().setText(filter == null ? CommonBundle.message("action.text.all") : filter.getDisplayName());
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new FilterImagesAction(new ThemeFilter() {
      @Override
      public String getDisplayName() {
        return ImagesBundle.message("action.all.text");
      }

      @Override
      public boolean accepts(VirtualFile file) {
        return true;
      }

      @Override
      public boolean isApplicableToProject(Project project) {
        return true;
      }

      @Override
      public void setFilter(ThumbnailView view) {
        view.setFilter(this);
      }
    }));
    for (ThemeFilter filter : ThemeFilter.EP_NAME.getExtensionList()) {
      group.add(new FilterImagesAction(filter));
    }

    return group;
  }
}
