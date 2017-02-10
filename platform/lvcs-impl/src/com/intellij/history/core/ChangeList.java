/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.core;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Clock;
import com.intellij.util.Consumer;
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

  public synchronized boolean endChangeSet(String name) {
    LocalHistoryLog.LOG.assertTrue(myChangeSetDepth > 0, "not balanced 'begin/end-change set' calls");

    myChangeSetDepth--;
    if (myChangeSetDepth > 0) return false;

    return doEndChangeSet(name);
  }

  private boolean doEndChangeSet(String name) {
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
    return new Iterable<ChangeSet>() {
      public Iterator<ChangeSet> iterator() {
        return new Iterator<ChangeSet>() {
          private final TIntHashSet recursionGuard = new TIntHashSet(1000);

          private ChangeSetHolder currentBlock;
          private ChangeSet next = fetchNext();

          public boolean hasNext() {
            return next != null;
          }

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