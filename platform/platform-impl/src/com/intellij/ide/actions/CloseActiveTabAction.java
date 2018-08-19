// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerUtil;
import org.jetbrains.annotations.NotNull;

public class CloseActiveTabAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(e.getDataContext(), true);
    boolean processed = false;
    if (contentManager != null && contentManager.canCloseContents()) {
      final Content selectedContent = contentManager.getSelectedContent();
      if (selectedContent != null && selectedContent.isCloseable()) {
        contentManager.removeContent(selectedContent, true);
        processed = true;
      }
    }

    if (!processed && contentManager != null) {
      final DataContext context = DataManager.getInstance().getDataContext(contentManager.getComponent());
      final ToolWindow tw = PlatformDataKeys.TOOL_WINDOW.getData(context);
      if (tw != null) {
        tw.hide(null);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    ContentManager contentManager=ContentManagerUtil.getContentManagerFromContext(event.getDataContext(), true);
    presentation.setEnabled(contentManager != null && contentManager.canCloseContents());

    if (!presentation.isEnabled() && contentManager != null) {
      final DataContext context = DataManager.getInstance().getDataContext(contentManager.getComponent());
      presentation.setEnabled(PlatformDataKeys.TOOL_WINDOW.getData(context) != null);
    }
  }
}