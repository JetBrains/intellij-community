package com.intellij.openapi.util.diff.requests;

import org.jetbrains.annotations.Nullable;

public class LoadingDiffRequest extends MessageDiffRequest {
  public LoadingDiffRequest(@Nullable String title) {
    super(title, "Loading...");
  }

  public LoadingDiffRequest() {
    super("Loading...");
  }
}
