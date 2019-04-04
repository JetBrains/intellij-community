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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class DiffDialogHints {
  @NotNull public static final DiffDialogHints DEFAULT = new DiffDialogHints(null);
  @NotNull public static final DiffDialogHints FRAME = new DiffDialogHints(WindowWrapper.Mode.FRAME);
  @NotNull public static final DiffDialogHints MODAL = new DiffDialogHints(WindowWrapper.Mode.MODAL);
  @NotNull public static final DiffDialogHints NON_MODAL = new DiffDialogHints(WindowWrapper.Mode.NON_MODAL);

  //
  // Impl
  //

  @Nullable private final WindowWrapper.Mode myMode;
  @Nullable private Component myParent;

  public DiffDialogHints(@Nullable WindowWrapper.Mode mode) {
    this(mode, null);
  }

  public DiffDialogHints(@Nullable WindowWrapper.Mode mode, @Nullable Component parent) {
    myMode = mode;
    myParent = parent;
  }

  public void setParent(@Nullable Component value) {
    myParent = value;
  }

  //
  // Getters
  //

  @Nullable
  public WindowWrapper.Mode getMode() {
    return myMode;
  }

  @Nullable
  public Component getParent() {
    return myParent;
  }
}
