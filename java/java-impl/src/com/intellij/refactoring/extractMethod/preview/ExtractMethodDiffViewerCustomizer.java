// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffExtension;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class ExtractMethodDiffViewerCustomizer extends DiffExtension {
  @CalledInAwt
  @Override
  public void onViewerCreated(@NotNull FrameDiffTool.DiffViewer viewer, @NotNull DiffContext context, @NotNull DiffRequest request) {
    if (request instanceof PreviewDiffRequest) {
      ((PreviewDiffRequest)request).setViewer(viewer);
    }
  }
}
