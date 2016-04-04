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
package com.intellij.diff.tools.simple;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

public class SimpleDiffTool implements FrameDiffTool {
  public static final SimpleDiffTool INSTANCE = new SimpleDiffTool();

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (SimpleOnesideDiffViewer.canShowRequest(context, request)) return new SimpleOnesideDiffViewer(context, request);
    if (SimpleDiffViewer.canShowRequest(context, request)) return new SimpleDiffViewer(context, request);
    if (SimpleThreesideDiffViewer.canShowRequest(context, request)) return new SimpleThreesideDiffViewer(context, request);
    throw new IllegalArgumentException(request.toString());
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return SimpleOnesideDiffViewer.canShowRequest(context, request) ||
           SimpleDiffViewer.canShowRequest(context, request) ||
           SimpleThreesideDiffViewer.canShowRequest(context, request);
  }

  @NotNull
  @Override
  public String getName() {
    return "Side-by-side viewer";
  }
}
