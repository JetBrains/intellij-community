// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface ToolWindowViewModelListener {
  @Topic.ProjectLevel
  Topic<ToolWindowViewModelListener> TOPIC = Topic.create("com.intellij.toolWindowListener", ToolWindowViewModelListener.class);

  void toolWindowChanged(@NotNull String id);

  void toolWindowsChanged();
}