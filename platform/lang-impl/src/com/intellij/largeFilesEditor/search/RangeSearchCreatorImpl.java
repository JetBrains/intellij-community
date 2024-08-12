// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.search;

import com.intellij.largeFilesEditor.search.searchResultsPanel.RangeSearch;
import com.intellij.largeFilesEditor.search.searchResultsPanel.RangeSearchCallback;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewContentManager;
import org.jetbrains.annotations.NotNull;

public final class RangeSearchCreatorImpl implements RangeSearchCreator {

  @Override
  public @NotNull RangeSearch createContent(Project project,
                                            VirtualFile virtualFile,
                                            String titleName) {
    RangeSearchCallback rangeSearchCallback = new RangeSearchCallbackImpl();
    RangeSearch rangeSearch = new RangeSearch(virtualFile, project, rangeSearchCallback);
    Content content = UsageViewContentManager.getInstance(project).addContent(
      titleName, true, rangeSearch.getComponent(), false, true);
    rangeSearch.setContent(content);

    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.FIND).activate(null, true);

    content.setDisposer(new Disposable() {
      @Override
      public void dispose() {
        rangeSearch.dispose();
      }
    });

    return rangeSearch;
  }
}
