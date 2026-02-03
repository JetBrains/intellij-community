// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface CommitNodeUiRenderExtension {
  void render(@NotNull Project project, @NotNull ColoredTreeCellRenderer renderer, @NotNull CommitNode node);
}
