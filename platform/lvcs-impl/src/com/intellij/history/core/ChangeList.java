// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.NlsContexts;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChangeList {
  private final ChangeListStorage myStorage;

  private int myChangeSetDepth;
  private ChangeSet myCurrentChangeSet;

  private int myIntervalBetweenActivities = 12 * 60 * 60 * 1000; // 12 hours

  public ChangeList(ChangeListStorage storage) {
    myStorage = storage;
  }

  public synchronized void close() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      LocalHistoryLog.LOG.assertTrue(myCurrentChangeSet == null || myCurrentChangeSet.isEmpty(),
                                     "current changes won't be saved: " + myCurrentChangeSet);
    }
    myStorage.close();
  }

  public synchronized long nextId() {
    return myStorage.nextId();
  }

  public synchronized void addChange(Change c) {
    assert myChangeSetDepth != 0;
    myCurrentChangeSet.addChange(c);
  }

  public synchronized void beginChangeSet() {
    myChangeSetDepth++;
    if (myChangeSetDepth > 1) return;

    doBeginChangeSet();
  }

  private void doBeginChangeSet() {
    myCurrentChangeSet = new ChangeSet(nextId(), Clock.getTime());
  }

  public synchronized boolean forceBeginChangeSet() {
    boolean split = myChangeSetDepth > 0;
    if (split) doEndChangeSet(null);

    myChangeSetDepth++;
    doBeginChangeSet();
    return split;
  }

  public synchronized boolean endChangeSet(@NlsContexts.Label String name) {
    LocalHistoryLog.LOG.assertTrue(myChangeSetDepth > 0, "not balanced 'begin/end-change set' calls");

    myChangeSetDepth--;
    if (myChangeSetDepth > 0) return false;

    return doEndChangeSet(name);
  }

  private boolean doEndChangeSet(@NlsContexts.Label String name) {
    if (myCurrentChangeSet.isEmpty()) {
      myCurrentChangeSet = null;
      return false;
    }

    myCurrentChangeSet.setName(name);
    myCurrentChangeSet.lock();

    myStorage.writeNextSet(myCurrentChangeSet);
    myCurrentChangeSet = null;

    return true;
  }

  @TestOnly
  public List<ChangeSet> getChangesInTests() {
    List<ChangeSet> result = new ArrayList<>();
    for (ChangeSet each : iterChanges()) {
      result.add(each);
    }
    return result;
  }

  // todo synchronization issue: changeset may me modified while being iterated
  public synchronized Iterable<ChangeSet> iterChanges() {
    return new Iterable<>() {
      @Override
      public Iterator<ChangeSet> iterator() {
        return new Iterator<>() {
          private final TIntHashSet recursionGuard = new TIntHashSet(1000);

          private ChangeSetHolder currentBlock;
          private ChangeSet next = fetchNext();

          @Override
          public boolean hasNext() {
            return next != null;
          }

          @Override
          public ChangeSet next() {
            ChangeSet result = next;
            next = fetchNext();
            return result;
          }

          private ChangeSet fetchNext() {
            if (currentBlock == null) {
              synchronized (ChangeList.this) {
                if (myCurrentChangeSet != null) {
                  currentBlock = new ChangeSetHolder(-1, myCurrentChangeSet);
                }
                else {
                  currentBlock = myStorage.readPrevious(-1, recursionGuard);
                }
              }
            }
            else {
              synchronized (ChangeList.this) {
                currentBlock = myStorage.readPrevious(currentBlock.id, recursionGuard);
              }
            }
            if (currentBlock == null) return null;
            return currentBlock.changeSet;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public void accept(ChangeVisitor v) {
    try {
      for (ChangeSet change : iterChanges()) {
        change.accept(v);
      }
    }
    catch (ChangeVisitor.StopVisitingException e) {
    }
    v.finished();
  }

  public synchronized void purgeObsolete(long period) {
    myStorage.purge(period, myIntervalBetweenActivities, changeSet -> {
      for (Content each : changeSet.getContentsToPurge()) {
        each.release();
      }
    });
  }

  @TestOnly
  public void setIntervalBetweenActivities(int value) {
    myIntervalBetweenActivities = value;
  }
}