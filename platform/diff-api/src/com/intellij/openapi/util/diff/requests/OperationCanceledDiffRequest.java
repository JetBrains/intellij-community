package com.intellij.openapi.util.diff.requests;

import org.jetbrains.annotations.Nullable;

public class OperationCanceledDiffRequest extends MessageDiffRequest {
  public OperationCanceledDiffRequest(@Nullable String title) {
    super(title, "Operation canceled");
  }

  public OperationCanceledDiffRequest() {
    super("Operation canceled");
  }
}
