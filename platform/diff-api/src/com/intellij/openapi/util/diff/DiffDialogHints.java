/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class DiffDialogHints {
  @NotNull public static final DiffDialogHints DEFAULT = new DiffDialogHints(null);
  @NotNull public static final DiffDialogHints FRAME = new DiffDialogHints(true);
  @NotNull public static final DiffDialogHints MODAL = new DiffDialogHints(false);

  //
  // Impl
  //

  @Nullable private final Boolean myFrame;
  @Nullable private final Component myParent;

  public DiffDialogHints(@Nullable Boolean frame) {
    this(frame, null);
  }

  public DiffDialogHints(@Nullable Boolean frame, @Nullable Component parent) {
    myFrame = frame;
    myParent = parent;
  }

  //
  // Getters
  //

  @Nullable
  public Boolean getFrame() {
    return myFrame;
  }

  @Nullable
  public Component getParent() {
    return myParent;
  }
}
