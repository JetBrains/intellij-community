/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.diff;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

/**
 * @see com.intellij.openapi.actionSystem.IdeActions#DIFF_VIEWER_TOOLBAR
 * @see com.intellij.openapi.actionSystem.IdeActions#DIFF_VIEWER_POPUP
 * @see com.intellij.openapi.actionSystem.IdeActions#GROUP_DIFF_EDITOR_POPUP
 */
public abstract class DiffExtension {
  public static final ExtensionPointName<DiffExtension> EP_NAME = ExtensionPointName.create("com.intellij.diff.DiffExtension");

  /**
   * Can be used to extend existing DiffViewers without registering new DiffTool
   *
   * @see com.intellij.diff.tools.util.base.DiffViewerListener
   * @see com.intellij.diff.tools.simple.SimpleDiffViewer
   * @see com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
   * @see com.intellij.diff.tools.fragmented.UnifiedDiffViewer
   */
  @RequiresEdt
  public abstract void onViewerCreated(@NotNull FrameDiffTool.DiffViewer viewer,
                                       @NotNull DiffContext context,
                                       @NotNull DiffRequest request);
}
