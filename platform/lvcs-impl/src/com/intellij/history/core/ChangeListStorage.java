// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

@ApiStatus.Internal
public interface ChangeListStorage {
  void close(boolean drop);

  void flush();

  /**
   * Generate a unique ID
   * Can be used for a new changeset or a change (!)
   */
  long nextId();

  /**
   * Purges the changesets older than the specified [period].
   * Changesets are considered "related" and are not purged if their timestamps are closer than [intervalBetweenActivities].
   */
  void purge(long period, long intervalBetweenActivities);

  void writeNextSet(@NotNull ChangeSet changeSet);

  /**
   * Returns an iterator over the change sets in the storage.
   *
   * @return iterator over change sets, starting from the last written change set at the time of the call
   */
  @NotNull Iterator<@NotNull ChangeSet> iterate();
}