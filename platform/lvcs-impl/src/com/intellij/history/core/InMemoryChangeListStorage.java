// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.util.Consumer;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryChangeListStorage implements ChangeListStorage {
  private int myCurrentId;
  private final List<ChangeSet> mySets = new ArrayList<>();

  @Override
  public void close() {
  }

  @Override
  public void force() {
  }

  @Override
  public long nextId() {
    return myCurrentId++;
  }

  @Override
  public @Nullable ChangeSetHolder readPrevious(int id, IntSet recursionGuard) {
    if (mySets.isEmpty()) return null;
    if (id == -1) return new ChangeSetHolder(mySets.size() - 1, mySets.get(mySets.size() - 1));
    return id == 0 ? null : new ChangeSetHolder(id -1, mySets.get(id - 1));
  }

  @Override
  public void writeNextSet(ChangeSet changeSet) {
    mySets.add(changeSet);
  }

  @Override
  public void purge(long period, int intervalBetweenActivities, Consumer<? super ChangeSet> processor) {
  }
}
