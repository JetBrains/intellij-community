// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.util.ui.EdtInvocationManager;
import org.jetbrains.annotations.NotNull;

/**
 * Serves the same purposes as {@link EdtInvocationManager} - allows to enhance intellij threading model in particular environment.
 *
 * @author Denis Zhdanov
 */
public class AlarmFactory {
  @NotNull private static final AlarmFactory ourInstance = new AlarmFactory();

  @NotNull
  public static AlarmFactory getInstance() {
    return ourInstance;
  }

  @NotNull
  public Alarm create() {
    return new Alarm();
  }

  @NotNull
  public Alarm create(@NotNull Alarm.ThreadToUse threadToUse) {
    return new Alarm(threadToUse);
  }

  @NotNull
  public Alarm create(@NotNull Alarm.ThreadToUse threadToUse, @NotNull Disposable parentDisposable) {
    return new Alarm(threadToUse, parentDisposable);
  }
}
