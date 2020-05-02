// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContext;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Soroka
 */
public interface BuildContentManager {
  String TOOL_WINDOW_ID = "Build";

  @NotNull
  static BuildContentManager getInstance(@NotNull Project project) {
    return project.getService(BuildContentManager.class);
  }

  void addContent(Content content);

  @NotNull
  ToolWindow getOrCreateToolWindow();

  void removeContent(final Content content);

  Content addTabbedContent(@NotNull JComponent contentComponent,
                           @NotNull String groupPrefix,
                           @NotNull @NlsContexts.DialogTitle String tabName,
                           @Nullable Icon icon,
                           @Nullable Disposable childDisposable);

  void setSelectedContent(Content content,
                          boolean requestFocus,
                          boolean forcedFocus,
                          boolean activate,
                          Runnable activationCallback);
}
