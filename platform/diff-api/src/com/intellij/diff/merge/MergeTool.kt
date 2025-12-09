// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface MergeTool {
  ExtensionPointName<MergeTool> EP_NAME = ExtensionPointName.create("com.intellij.diff.merge.MergeTool");

  /**
   * Creates viewer for the given request. Clients should call {@link #canShow(MergeContext, MergeRequest)} first.
   */
  @RequiresEdt
  @NotNull
  MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request);

  boolean canShow(@NotNull MergeContext context, @NotNull MergeRequest request);

  /**
   * Merge viewer should call {@link MergeContext#finishMerge(MergeResult)} when processing is over.
   * <p>
   * {@link MergeRequest#applyResult(MergeResult)} will be performed by the caller, so it shouldn't be called by MergeViewer directly.
   */
  interface MergeViewer extends Disposable {
    /**
     * The component will be used for {@link com.intellij.openapi.actionSystem.ActionToolbar#setTargetComponent(JComponent)}
     * and might want to implement {@link com.intellij.openapi.actionSystem.UiDataProvider} for {@link ToolbarComponents#toolbarActions}.
     */
    @NotNull
    JComponent getComponent();

    @Nullable
    JComponent getPreferredFocusedComponent();

    /**
     * @return Action that should be triggered on the corresponding action.
     * <p/>
     * Typical implementation can perform some checks and either call finishMerge(result) or do nothing
     * <p/>
     * return null if action is not available
     */
    @Nullable
    Action getResolveAction(@NotNull MergeResult result);

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
    public @Nullable JComponent statusPanel;

    /**
     * return false if merge window should be prevented from closing and canceling resolve.
     */
    public @Nullable BooleanGetter closeHandler;
  }
}
