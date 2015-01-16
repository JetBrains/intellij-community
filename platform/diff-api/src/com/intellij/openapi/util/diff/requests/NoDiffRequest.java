package com.intellij.openapi.util.diff.requests;

import org.jetbrains.annotations.Nullable;

public class NoDiffRequest extends MessageDiffRequest {
  public NoDiffRequest(@Nullable String title) {
    super(title, "Nothing to show");
  }

  public NoDiffRequest() {
    super("Nothing to show");
  }
}
