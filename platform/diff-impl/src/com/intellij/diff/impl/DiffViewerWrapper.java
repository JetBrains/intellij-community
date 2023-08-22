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
package com.intellij.diff.impl;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * WARNING: This is not an extension point you are looking for.
 * <p/>
 * Please, consider using {@link com.intellij.diff.DiffTool}, {@link com.intellij.diff.DiffExtension}
 * or introducing a better designed extension point into the platform, rather than adding a second usage of this one.
 */
@ApiStatus.Internal
public interface DiffViewerWrapper {
  Key<DiffViewerWrapper> KEY = Key.create("Diff.DiffViewerWrapper");

  DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request, @NotNull DiffViewer wrappedViewer);
}
