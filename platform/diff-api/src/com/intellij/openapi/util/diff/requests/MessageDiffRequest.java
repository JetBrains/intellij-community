package com.intellij.openapi.util.diff.requests;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MessageDiffRequest extends DiffRequestBase {
  @Nullable private String myTitle;
  @NotNull private String myMessage;

  public MessageDiffRequest(@NotNull String message) {
    this(null, message);
  }

  public MessageDiffRequest(@Nullable String title, @NotNull String message) {
    myTitle = title;
    myMessage = message;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  public String getMessage() {
    return myMessage;
  }

  public void setTitle(@Nullable String title) {
    myTitle = title;
  }

  public void setMessage(@NotNull String message) {
    myMessage = message;
  }
}
