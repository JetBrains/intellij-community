package com.intellij.openapi.util.diff.requests;

import org.jetbrains.annotations.NotNull;

public class NoDiffRequest extends DiffRequestBase {
  public static final NoDiffRequest INSTANCE = new NoDiffRequest();

  @NotNull
  @Override
  public String getTitle() {
    return "Nothing To Show";
  }
}
