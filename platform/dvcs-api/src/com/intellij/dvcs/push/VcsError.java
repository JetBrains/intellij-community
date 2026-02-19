// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import com.intellij.dvcs.ui.DvcsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsError {
  private final @NotNull @Nls String myErrorText;
  private final @Nullable VcsErrorHandler myErrorHandleListener;

  public VcsError(@NotNull @Nls String text) {
    this(text, null);
  }

  public VcsError(@NotNull @Nls String text, @Nullable VcsErrorHandler listener) {
    myErrorText = text;
    myErrorHandleListener = listener;
  }

  public @Nls String getText() {
    return myErrorText;
  }

  public void handleError(@NotNull CommitLoader loader) {
    if (myErrorHandleListener != null) {
      myErrorHandleListener.handleError(loader);
    }
  }

  public static VcsError createEmptyTargetError(@NotNull @Nls String name) {
    return new VcsError(DvcsBundle.message("push.error.specify.not.empty.remote.push.path.0", name));
  }
}
