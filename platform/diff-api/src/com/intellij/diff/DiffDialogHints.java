// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff;

import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class DiffDialogHints {
  public static final @NotNull DiffDialogHints DEFAULT = new DiffDialogHints(null);
  public static final @NotNull DiffDialogHints FRAME = new DiffDialogHints(WindowWrapper.Mode.FRAME);
  public static final @NotNull DiffDialogHints MODAL = new DiffDialogHints(WindowWrapper.Mode.MODAL);
  public static final @NotNull DiffDialogHints NON_MODAL = new DiffDialogHints(WindowWrapper.Mode.NON_MODAL);

  private final @Nullable WindowWrapper.Mode myMode;
  private final @Nullable Component myParent;
  private final @Nullable Consumer<WindowWrapper> myWindowConsumer;

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

  public @Nullable WindowWrapper.Mode getMode() {
    return myMode;
  }

  public @Nullable Component getParent() {
    return myParent;
  }

  /**
   * NB: Consumer might not be called at all (ex: for external diff/merge tools, that do not spawn WindowWrapper)
   */
  public @Nullable Consumer<WindowWrapper> getWindowConsumer() {
    return myWindowConsumer;
  }
}
