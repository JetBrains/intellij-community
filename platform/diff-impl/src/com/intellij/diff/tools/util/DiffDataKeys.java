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
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;

public interface DiffDataKeys {
  DataKey<Editor> CURRENT_EDITOR = DataKey.create("diff_current_editor");
  DataKey<DiffContent> CURRENT_CONTENT = DataKey.create("diff_current_content");
  DataKey<LineRange> CURRENT_CHANGE_RANGE = DataKey.create("diff_current_change_range");

  DataKey<DiffRequest> DIFF_REQUEST = DataKey.create("diff_request");
  DataKey<DiffContext> DIFF_CONTEXT = DataKey.create("diff_context");
  DataKey<FrameDiffTool.DiffViewer> DIFF_VIEWER = DataKey.create("diff_frame_viewer");
  DataKey<FrameDiffTool.DiffViewer> WRAPPING_DIFF_VIEWER = DataKey.create("main_diff_frame_viewer"); // if DiffViewerWrapper is used
  DataKey<OpenFileDescriptor> OPEN_FILE_DESCRIPTOR = DataKey.create("diff_open_file_descriptor");

  DataKey<PrevNextDifferenceIterable> PREV_NEXT_DIFFERENCE_ITERABLE = DataKey.create("prev_next_difference_iterable");
}
