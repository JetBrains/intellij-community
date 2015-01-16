package com.intellij.openapi.util.diff.requests;

import org.jetbrains.annotations.NotNull;

public class LoadingDiffRequest extends DiffRequestBase {
  @NotNull private final String myTitle;

  public LoadingDiffRequest() {
    this("Loading diff...");
  }

  public LoadingDiffRequest(@NotNull String message) {
    myTitle = message;
  }

  @NotNull
  @Override
  public String getTitle() {
    return myTitle;
  }
}
