// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.favoritesTreeView.FavoritesPanel;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class FavoritesViewToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public boolean isApplicable(@NotNull Project project) {
    return Registry.is("ide.favorites.tool.window.applicable", false);
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    FavoritesTreeViewPanel panel = new FavoritesPanel(project).getPanel();
    panel.setupToolWindow((ToolWindowEx)toolWindow);
    ContentManager contentManager = toolWindow.getContentManager();
    Content content = contentManager.getFactory().createContent(panel, null, false);
    contentManager.addContent(content);
  }
}
