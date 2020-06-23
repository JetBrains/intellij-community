// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * A task that should be executed in IDE dumb mode, via {@link DumbService#queueTask(DumbModeTask)}.
 *
 * @author peter
 */
public abstract class DumbModeTask implements Disposable {
  private final Object myEquivalenceObject;

  /**
   * @deprecated using of that constructor likely disables merging
   * of similar tasks in the {@link DumbService#queueTask(DumbModeTask)}.
   * Use the {@link DumbModeTask#DumbModeTask(Object)} to specify equivalence
   * clearly.
   * @see #getEquivalenceObject() for details
   */
  @Deprecated
  public DumbModeTask() {
    myEquivalenceObject = this;
  }

  /**
   * @param equivalenceObject see {@link #getEquivalenceObject()}
   */
  public DumbModeTask(@NotNull Object equivalenceObject) {
    myEquivalenceObject = Pair.create(getClass(), equivalenceObject);
  }

  /**
   * @return an object whose {@link Object#equals(Object)} determines task equivalence.
   * If several equivalent tasks of the same class are queued for dumb mode execution at once,
   * only one of them will be executed.
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
