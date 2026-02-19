// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.requests;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MessageDiffRequest extends DiffRequest {
  private @Nullable @NlsContexts.DialogTitle String myTitle;
  private @NotNull @Nls String myMessage;

  public MessageDiffRequest(@NotNull @Nls String message) {
    this(null, message);
  }

  public MessageDiffRequest(@Nullable @NlsContexts.DialogTitle String title, @NotNull @Nls String message) {
    myTitle = title;
    myMessage = message;
  }

  @Override
  public @Nullable String getTitle() {
    return myTitle;
  }

  public @Nls @NotNull String getMessage() {
    return myMessage;
  }

  public void setTitle(@Nullable @NlsContexts.DialogTitle String title) {
    myTitle = title;
  }

  public void setMessage(@NotNull @Nls String message) {
    myMessage = message;
  }

  @Override
  public final void onAssigned(boolean isAssigned) {
  }
}
