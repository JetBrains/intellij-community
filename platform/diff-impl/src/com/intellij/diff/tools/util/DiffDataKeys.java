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

public interface DiffDataKeys {
  DataKey<Navigatable> NAVIGATABLE = DataKey.create("diff_navigatable");
  DataKey<Navigatable[]> NAVIGATABLE_ARRAY = DataKey.create("diff_navigatable_array");

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

  DataKey<PrevNextDifferenceIterable> PREV_NEXT_DIFFERENCE_ITERABLE = DataKey.create("prev_next_difference_iterable");
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
