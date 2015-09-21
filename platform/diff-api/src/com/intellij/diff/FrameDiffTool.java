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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface FrameDiffTool extends DiffTool {
  /**
   * Creates viewer for the given request. Clients should call {@link #canShow(DiffContext, DiffRequest)} first.
   */
  @CalledInAwt
  @NotNull
  DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request);

  interface DiffViewer extends Disposable {
    @NotNull
    JComponent getComponent();

    @Nullable
    JComponent getPreferredFocusedComponent();

    /**
     * Should be called after adding {@link #getComponent()} to the components hierarchy.
     */
    @NotNull
    @CalledInAwt
    ToolbarComponents init();

    @Override
    @CalledInAwt
    void dispose();
  }

  class ToolbarComponents {
    @Nullable public List<AnAction> toolbarActions;
    @Nullable public List<AnAction> popupActions;
    @Nullable public JComponent statusPanel;
  }
}
