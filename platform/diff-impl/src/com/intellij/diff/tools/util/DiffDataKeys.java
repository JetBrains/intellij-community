// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.merge.MergeTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vcs.changes.DiffPreview;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;

public interface DiffDataKeys {
  DataKey<Navigatable> NAVIGATABLE = DataKey.create("diff_navigatable");
  DataKey<Navigatable[]> NAVIGATABLE_ARRAY = DataKey.create("diff_navigatable_array");
  @ApiStatus.Internal
  DataKey<Runnable> NAVIGATION_CALLBACK = DataKey.create("diff_after_navigate_callback");

  DataKey<Editor> CURRENT_EDITOR = DataKey.create("diff_current_editor");
  DataKey<DiffContent> CURRENT_CONTENT = DataKey.create("diff_current_content");
  DataKey<LineRange> CURRENT_CHANGE_RANGE = DataKey.create("diff_current_change_range");

  DataKey<DiffRequest> DIFF_REQUEST = DataKey.create("diff_request");
  DataKey<DiffContext> DIFF_CONTEXT = DataKey.create("diff_context");
  DataKey<FrameDiffTool.DiffViewer> DIFF_VIEWER = DataKey.create("diff_frame_viewer");
  /**
   * @see com.intellij.diff.impl.DiffViewerWrapper
   */
  DataKey<FrameDiffTool.DiffViewer> WRAPPING_DIFF_VIEWER = DataKey.create("main_diff_frame_viewer");

  DataKey<MergeTool.MergeViewer> MERGE_VIEWER = DataKey.create("merge_viewer");

  @ApiStatus.Internal
  DataKey<PrevNextDifferenceIterable> PREV_NEXT_DIFFERENCE_ITERABLE = DataKey.create("prev_next_difference_iterable");
  @ApiStatus.Internal
  DataKey<CrossFilePrevNextDifferenceIterableSupport> CROSS_FILE_PREV_NEXT_DIFFERENCE_ITERABLE = DataKey.create("corss_file_prev_next_difference_iterable");
  @ApiStatus.Internal
  DataKey<PrevNextFileIterable> PREV_NEXT_FILE_ITERABLE = DataKey.create("prev_next_file_iterable");
  DataKey<DiffChangedRangeProvider> EDITOR_CHANGED_RANGE_PROVIDER = DataKey.create("diff_changed_range_provider");

  /**
   * A simple way to enable a "Compare Files" action for a given context.
   * <p>
   * Not to be confused with {@link #DIFF_REQUEST} key that is available in context of
   * an already-visible {@link FrameDiffTool.DiffViewer}.
   * <p>
   * See also {@link com.intellij.diff.actions.ShowDiffAction} and {@link AnActionExtensionProvider} for more flexibility.
   */
  DataKey<DiffRequest> DIFF_REQUEST_TO_COMPARE = DataKey.create("diff_request_to_compare");

  DataKey<DiffPreview> EDITOR_TAB_DIFF_PREVIEW = DataKey.create("EditorTabDiffPreview");
}
