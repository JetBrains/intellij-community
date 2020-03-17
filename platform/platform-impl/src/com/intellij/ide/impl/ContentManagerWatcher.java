// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import org.jetbrains.annotations.NotNull;

public final class ContentManagerWatcher {
  private final ToolWindow myToolWindow;
  private final ContentManager myContentManager;

  public ContentManagerWatcher(@NotNull ToolWindow toolWindow, @NotNull ContentManager contentManager) {
    myToolWindow = toolWindow;
    myContentManager = contentManager;
    myToolWindow.setAvailable(contentManager.getContentCount() > 0, null);

    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void contentAdded(@NotNull ContentManagerEvent e) {
        myToolWindow.setAvailable(true, null);
      }

      @Override
      public void contentRemoved(@NotNull ContentManagerEvent e) {
        myToolWindow.setAvailable(myContentManager.getContentCount() > 0, null);
      }
    });
  }
}