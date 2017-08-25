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
package com.intellij.diff.tools.dir;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.internal.statistic.UsageTrigger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DirDiffTool implements FrameDiffTool {
  public static final DirDiffTool INSTANCE = new DirDiffTool();

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    UsageTrigger.trigger("diff.DirDiffViewer");
    return createViewer(context, (ContentDiffRequest)request);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return DirDiffViewer.canShowRequest(context, request);
  }

  @NotNull
  @Override
  public String getName() {
    return "Directory viewer";
  }

  @NotNull
  public static FrameDiffTool.DiffViewer createViewer(@NotNull DiffContext context,
                                                      @NotNull ContentDiffRequest request) {
    return new DirDiffViewer(context, request);
  }

  @NotNull
  public static FrameDiffTool.DiffViewer createViewer(@NotNull DiffContext context,
                                                      @NotNull DiffElement element1,
                                                      @NotNull DiffElement element2,
                                                      @NotNull DirDiffSettings settings,
                                                      @Nullable String helpID) {
    return new DirDiffViewer(context, element1, element2, settings, helpID);
  }
}
