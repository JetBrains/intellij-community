// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface FrameDiffTool extends DiffTool {
  /**
   * Creates viewer for the given request. Clients should call {@link #canShow(DiffContext, DiffRequest)} first.
   */
  @RequiresEdt
  @NotNull
  DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request);

  interface DiffViewer extends Disposable {
    @NotNull
    JComponent getComponent();

    @Nullable
    JComponent getPreferredFocusedComponent();

    /**
     * Should be called after adding {@link #getComponent()} to the components hierarchy.
     */
    @NotNull
    @RequiresEdt
    ToolbarComponents init();

    @Override
    @RequiresEdt
    void dispose();
  }

  class ToolbarComponents {
    @Nullable public List<AnAction> toolbarActions;
    @Nullable public List<AnAction> popupActions;
    @Nullable public JComponent statusPanel;
    @Nullable public DiffInfo diffInfo;
    public boolean needTopToolbarBorder = false;
  }

  @ApiStatus.Experimental
  interface DiffInfo {
    @NotNull JComponent getComponent();
  }
}
