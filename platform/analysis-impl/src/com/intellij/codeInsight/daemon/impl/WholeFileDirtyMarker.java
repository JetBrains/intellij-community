// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class WholeFileDirtyMarker implements RangeMarker {
  static final RangeMarker INSTANCE = new WholeFileDirtyMarker();

  private WholeFileDirtyMarker() {}

  @Override
  public @NotNull Document getDocument() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getStartOffset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getEndOffset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public void setGreedyToLeft(boolean greedy) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setGreedyToRight(boolean greedy) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isGreedyToRight() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isGreedyToLeft() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose() {
    // ignore
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNls String toString() {
    return "WHOLE_FILE";
  }
}
