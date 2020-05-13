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

import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class DiffDialogHints {
  @NotNull public static final DiffDialogHints DEFAULT = new DiffDialogHints(null);
  @NotNull public static final DiffDialogHints FRAME = new DiffDialogHints(WindowWrapper.Mode.FRAME);
  @NotNull public static final DiffDialogHints MODAL = new DiffDialogHints(WindowWrapper.Mode.MODAL);
  @NotNull public static final DiffDialogHints NON_MODAL = new DiffDialogHints(WindowWrapper.Mode.NON_MODAL);

  @Nullable private final WindowWrapper.Mode myMode;
  @Nullable private final Component myParent;
  @Nullable private final Consumer<WindowWrapper> myWindowConsumer;

  public DiffDialogHints(@Nullable WindowWrapper.Mode mode) {
    this(mode, null);
  }

  public DiffDialogHints(@Nullable WindowWrapper.Mode mode, @Nullable Component parent) {
    this(mode, parent, null);
  }

  public DiffDialogHints(@Nullable WindowWrapper.Mode mode, @Nullable Component parent, @Nullable Consumer<WindowWrapper> windowConsumer) {
    myMode = mode;
    myParent = parent;
    myWindowConsumer = windowConsumer;
  }

  @Nullable
  public WindowWrapper.Mode getMode() {
    return myMode;
  }

  @Nullable
  public Component getParent() {
    return myParent;
  }

  /**
   * NB: Consumer might not be called at all (ex: for external diff/merge tools, that do not spawn WindowWrapper)
   */
  @Nullable
  public Consumer<WindowWrapper> getWindowConsumer() {
    return myWindowConsumer;
  }
}
