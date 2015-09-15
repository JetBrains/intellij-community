/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.merge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.BooleanGetter;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface MergeTool {
  ExtensionPointName<MergeTool> EP_NAME = ExtensionPointName.create("com.intellij.diff.merge.MergeTool");

  /**
   * Creates viewer for the given request. Clients should call {@link #canShow(MergeContext, MergeRequest)} first.
   */
  @CalledInAwt
  @NotNull
  MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request);

  boolean canShow(@NotNull MergeContext context, @NotNull MergeRequest request);

  /**
   * Merge viewer should call {@link MergeContext#finishMerge(MergeResult)} when processing is over.
   *
   * {@link MergeRequest#applyResult(MergeResult)} will be performed by the caller, so it shouldn't be called by MergeViewer directly.
   */
  interface MergeViewer extends Disposable {
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
    @CalledInAwt
    ToolbarComponents init();

    @Override
    @CalledInAwt
    void dispose();
  }

  class ToolbarComponents {
    @Nullable public List<AnAction> toolbarActions;
    @Nullable public JComponent statusPanel;

    /**
     * return false if merge window should be prevented from closing and canceling resolve.
     */
    @Nullable public BooleanGetter closeHandler;
  }
}
