// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link com.intellij.openapi.editor.FoldRegion}s with same FoldingGroup instances expand and collapse together.
 */
public final class FoldingGroup {
  private static final AtomicLong ourCounter = new AtomicLong();

  private final @NonNls @NotNull String myDebugName;
  private final long myId;

  private FoldingGroup(@NonNls @NotNull String debugName) {
    myDebugName = debugName;
    myId = ourCounter.incrementAndGet();
  }

  @Contract("_ -> new")
  public static @NotNull FoldingGroup newGroup(@NonNls @NotNull String debugName) {
    return new FoldingGroup(debugName);
  }

  @ApiStatus.Internal
  public long getId() {
    return myId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FoldingGroup group = (FoldingGroup)o;

    if (myId != group.myId) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(myId);
  }

  @Override
  public String toString() {
    return myDebugName;
  }
}
