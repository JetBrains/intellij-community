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
  @NotNull public static final DiffDialogHints NON_MODAL = new DiffDialogHints(false);
  @NotNull public static final DiffDialogHints MODAL = new DiffDialogHints(true);

  //
  // Impl
  //

  private final boolean myModal;
  @Nullable private final Component myParent;

  public DiffDialogHints() {
    this(false);
  }

  public DiffDialogHints(boolean modal) {
    this(modal, null);
  }

  public DiffDialogHints(boolean modal, @Nullable Component parent) {
    myModal = modal;
    myParent = parent;
  }

  //
  // Getters
  //

  public boolean isModal() {
    return myModal;
  }

  @Nullable
  public Component getParent() {
    return myParent;
  }
}
