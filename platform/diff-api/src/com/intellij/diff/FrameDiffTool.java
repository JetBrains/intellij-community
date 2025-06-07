// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * Implements diff viewer that is embedded into the common diff panels.
 * Such as used by {@link DiffManagerEx#showDiffBuiltin}, {@link DiffManager#createRequestPanel}
 * and other {@link com.intellij.diff.impl.DiffRequestProcessor} implementations.
 */
public interface FrameDiffTool extends DiffTool {
  /**
   * Creates viewer for the given request. Clients should call {@link #canShow(DiffContext, DiffRequest)} first.
   */
  @RequiresEdt
  @NotNull
  DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request);

  default @NotNull DiffToolType getToolType() {
    return DiffToolType.Default.INSTANCE;
  }

  interface DiffViewer extends Disposable {
    /**
     * The component will be used for {@link com.intellij.openapi.actionSystem.ActionToolbar#setTargetComponent(JComponent)}
     * and might want to implement {@link com.intellij.openapi.actionSystem.UiDataProvider} for {@link ToolbarComponents#toolbarActions}.
     */
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
    public @Nullable List<AnAction> toolbarActions;
    public @Nullable List<AnAction> popupActions;
    public @Nullable JComponent statusPanel;
    public @Nullable DiffInfo diffInfo;
    public boolean needTopToolbarBorder = false;
  }

  @ApiStatus.Experimental
  interface DiffInfo {
    @NotNull JComponent getComponent();
  }
}
