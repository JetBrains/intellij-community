// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates.github;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Outcome<V> {

  private final V myData;
  private final boolean myCancelled;
  private final Exception myException;

  private Outcome(V data, boolean cancelled, Exception exception) {
    myData = data;
    myCancelled = cancelled;
    myException = exception;
  }

  public @Nullable V get() {
    return myData;
  }

  public boolean isCancelled() {
    return myCancelled;
  }

  public @Nullable Exception getException() {
    return myException;
  }

  public static @NotNull <V> Outcome<V> createAsCancelled() {
    return new Outcome<>(null, true, null);
  }

  public static @NotNull <V> Outcome<V> createAsException(@NotNull Exception ex) {
    return new Outcome<>(null, false, ex);
  }

  public static @NotNull <V> Outcome<V> createNormal(@NotNull V data) {
    return new Outcome<>(data, false, null);
  }

}
