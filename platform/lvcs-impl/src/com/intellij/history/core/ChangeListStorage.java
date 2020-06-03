// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.util.Consumer;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.Nullable;

public interface ChangeListStorage {
  void close();

  long nextId();

  @Nullable
  ChangeSetHolder readPrevious(int id, TIntHashSet recursionGuard);

  void purge(long period, int intervalBetweenActivities, Consumer<? super ChangeSet> processor);

  void writeNextSet(ChangeSet changeSet);
}