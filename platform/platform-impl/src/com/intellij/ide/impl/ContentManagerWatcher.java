// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UtilityClassWithPublicConstructor")
public final class ContentManagerWatcher {
  /**
   * @deprecated Use {@link #watchContentManager}
   */
  @Deprecated
  public ContentManagerWatcher(@NotNull ToolWindow toolWindow, @NotNull ContentManager contentManager) {
    watchContentManager(toolWindow, contentManager);
  }

  public static void watchContentManager(@NotNull ToolWindow toolWindow, @NotNull ContentManager contentManager) {
    toolWindow.setAvailable(!contentManager.isEmpty());

    contentManager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentAdded(@NotNull ContentManagerEvent e) {
        toolWindow.setAvailable(true);
      }

      @Override
      public void contentRemoved(@NotNull ContentManagerEvent e) {
        if (!(toolWindow instanceof ToolWindowImpl) || !((ToolWindowImpl)toolWindow).getDecorator().isSplitUnsplitInProgress()) {
          toolWindow.setAvailable(!contentManager.isEmpty());
        }
      }
    });
  }
}