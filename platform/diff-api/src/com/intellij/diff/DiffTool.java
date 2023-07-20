// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point that allows registering new custom UI for comparing file revisions.
 * <p>
 * The supported tool types are currently limited by {@link FrameDiffTool}.
 *
 * @see DiffExtension
 * @see SuppressiveDiffTool
 * @see com.intellij.diff.merge.MergeTool
 */
public interface DiffTool {
  ExtensionPointName<DiffTool> EP_NAME = ExtensionPointName.create("com.intellij.diff.DiffTool");

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  String getName();

  boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request);
}
