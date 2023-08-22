// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  public V get() {
    return myData;
  }

  public boolean isCancelled() {
    return myCancelled;
  }

  @Nullable
  public Exception getException() {
    return myException;
  }

  @NotNull
  public static <V> Outcome<V> createAsCancelled() {
    return new Outcome<>(null, true, null);
  }

  @NotNull
  public static <V> Outcome<V> createAsException(@NotNull Exception ex) {
    return new Outcome<>(null, false, ex);
  }

  @NotNull
  public static <V> Outcome<V> createNormal(@NotNull V data) {
    return new Outcome<>(data, false, null);
  }

}
