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
package com.intellij.diff.tools.binary;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

public class BinaryDiffTool implements FrameDiffTool {
  public static final BinaryDiffTool INSTANCE = new BinaryDiffTool();

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (OnesideBinaryDiffViewer.canShowRequest(context, request)) return new OnesideBinaryDiffViewer(context, request);
    if (TwosideBinaryDiffViewer.canShowRequest(context, request)) return new TwosideBinaryDiffViewer(context, request);
    if (ThreesideBinaryDiffViewer.canShowRequest(context, request)) return new ThreesideBinaryDiffViewer(context, request);
    throw new IllegalArgumentException(request.toString());
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return OnesideBinaryDiffViewer.canShowRequest(context, request) ||
           TwosideBinaryDiffViewer.canShowRequest(context, request) ||
           ThreesideBinaryDiffViewer.canShowRequest(context, request);
  }

  @NotNull
  @Override
  public String getName() {
    return "Binary file viewer";
  }
}
