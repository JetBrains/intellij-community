// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard;

import com.intellij.ide.IdeCoreBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommitStepException extends Exception {

  @Override
  public @NotNull String getMessage() {
    return super.getMessage();
  }

  public CommitStepException(final @Nullable @Nls String message) {
    super(message != null ? message : IdeCoreBundle.message("unknown.error"));
  }
}
