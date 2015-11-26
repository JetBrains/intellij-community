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
package com.intellij.diff;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

public abstract class DiffExtension {
  public static final ExtensionPointName<DiffExtension> EP_NAME = ExtensionPointName.create("com.intellij.diff.DiffExtension");

  /**
   * Can be used to extend existing DiffViewers without registering new DiffTool
   *
   * @see DiffViewerListener, DiffViewerBase
   */
  @CalledInAwt
  public abstract void onViewerCreated(@NotNull FrameDiffTool.DiffViewer viewer,
                                       @NotNull DiffContext context,
                                       @NotNull DiffRequest request);
}
