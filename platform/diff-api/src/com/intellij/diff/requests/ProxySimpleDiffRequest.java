// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.requests;

import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ProxySimpleDiffRequest extends SimpleDiffRequest {
  private final @NotNull UserDataHolder myDataHolder;

  public ProxySimpleDiffRequest(@Nullable @NlsContexts.DialogTitle String title,
                                @NotNull List<DiffContent> contents,
                                @NotNull List<@Nls String> titles,
                                @NotNull UserDataHolder dataHolder) {
    super(title, contents, titles);
    myDataHolder = dataHolder;
  }

  @Override
  public final void onAssigned(boolean isAssigned) {
  }

  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
    return myDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDataHolder.putUserData(key, value);
  }
}
