/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
