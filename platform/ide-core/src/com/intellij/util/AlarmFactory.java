// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link Alarm} directly.
 */
public class AlarmFactory {
  private static final @NotNull AlarmFactory ourInstance = new AlarmFactory();

  public static @NotNull AlarmFactory getInstance() {
    return ourInstance;
  }

  public @NotNull Alarm create() {
    return new Alarm();
  }

  public @NotNull Alarm create(@NotNull Alarm.ThreadToUse threadToUse) {
    return new Alarm(threadToUse);
  }

  public @NotNull Alarm create(@NotNull Alarm.ThreadToUse threadToUse, @NotNull Disposable parentDisposable) {
    return new Alarm(threadToUse, parentDisposable);
  }
}
