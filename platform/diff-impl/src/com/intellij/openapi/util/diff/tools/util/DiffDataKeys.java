package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.requests.DiffRequest;

public interface DiffDataKeys {
  DataKey<DiffRequest> DIFF_REQUEST = DataKey.create("diff_request");
  DataKey<FrameDiffTool.DiffViewer> DIFF_VIEWER = DataKey.create("diff_frame_viewer");
  DataKey<OpenFileDescriptor> OPEN_FILE_DESCRIPTOR = DataKey.create("diff_open_file_descriptor");

  DataKey<PrevNextDifferenceIterable> PREV_NEXT_DIFFERENCE_ITERABLE = DataKey.create("prev_next_difference_iterable");
}
