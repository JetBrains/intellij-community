/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

/**
 * A task that should be executed in IDE dumb mode, via {@link DumbService#queueTask(DumbModeTask)}.
 *
 * @author peter
 */
public abstract class DumbModeTask implements Disposable {
  private final Object myEquivalenceObject;

  public DumbModeTask() {
    myEquivalenceObject = this;
  }

  /**
   * @param equivalenceObject see {@link #getEquivalenceObject()}
   */
  public DumbModeTask(@NotNull Object equivalenceObject) {
    myEquivalenceObject = equivalenceObject;
  }

  /**
   * @return an object whose {@link Object#equals(Object)} determines task equivalence. If several equivalent tasks are queued
   * for dumb mode execution at once, only one of them will be executed. By default the task object itself is returned.
   */
  @NotNull
  public final Object getEquivalenceObject() {
    return myEquivalenceObject;
  }

  public abstract void performInDumbMode(@NotNull ProgressIndicator indicator);

  @Override
  public void dispose() {
  }
}
