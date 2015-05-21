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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer;
import org.jetbrains.annotations.NotNull;

public class OnesideDiffTool implements FrameDiffTool {
  public static final OnesideDiffTool INSTANCE = new OnesideDiffTool();

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (SimpleOnesideDiffViewer.canShowRequest(context, request)) return new SimpleOnesideDiffViewer(context, request);
    if (OnesideDiffViewer.canShowRequest(context, request)) return new OnesideDiffViewer(context, request);
    throw new IllegalArgumentException(request.toString());
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return SimpleOnesideDiffViewer.canShowRequest(context, request) || OnesideDiffViewer.canShowRequest(context, request);
  }

  @NotNull
  @Override
  public String getName() {
    return "Oneside viewer";
  }
}
