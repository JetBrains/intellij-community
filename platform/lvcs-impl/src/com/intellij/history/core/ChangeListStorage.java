// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.util.Consumer;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ChangeListStorage {
  void close();

  void force();

  long nextId();

  @Nullable
  ChangeSetHolder readPrevious(int id, IntSet recursionGuard);

  void purge(long period, int intervalBetweenActivities, Consumer<? super ChangeSet> processor);

  void writeNextSet(ChangeSet changeSet);
}