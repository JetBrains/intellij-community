// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ValidationResult {
  private final @Nls String myMessage;
  private final @NlsContexts.DialogTitle String myTitle;
  private final @Nullable Runnable myQuickFix;
  private final boolean myIsWarning;

  public ValidationResult(@Nls String message, @NlsContexts.DialogTitle String title, @Nullable Runnable quickFix, boolean isWarning) {
    myMessage = message;
    myTitle = title;
    myQuickFix = quickFix;
    myIsWarning = isWarning;
  }

  public @Nls String getMessage() {
    return myMessage;
  }

  public @NlsContexts.DialogTitle String getTitle() {
    return myTitle;
  }

  public @Nullable Runnable getQuickFix() {
    return myQuickFix;
  }

  public boolean isWarning() {
    return myIsWarning;
  }
}
