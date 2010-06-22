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

import com.intellij.history.Clock;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.core.storage.Content;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChangeList {
  private final ChangeListStorage myStorage;

  private ChangeSetBlock myCurrentBlock;

  private ChangeSet myCurrentChangeSet;
  private int myChangeSetDepth;

  private int myIntervalBetweenActivities = 12 * 60 * 60 * 1000; // one day

  public ChangeList(ChangeListStorage storage) {
    myStorage = storage;
    myCurrentBlock = storage.createNewBlock();
  }

  public synchronized void save() {
    flushChanges(true);
  }

  public synchronized void close() {
    flushChanges(true);
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
    myCurrentChangeSet = new ChangeSet(myStorage.nextId(), Clock.getCurrentTimestamp());
    myCurrentBlock.add(myCurrentChangeSet);
  }

  public synchronized boolean forceBeginChangeSet() {
    boolean split = myChangeSetDepth > 0;
    if (split) doEndChangeSet(null);

    myChangeSetDepth++;
    doBeginChangeSet();
    return split;
  }

  public synchronized boolean endChangeSet(String name) {
    assert myChangeSetDepth > 0;

    myChangeSetDepth--;
    if (myChangeSetDepth > 0) return false;

    return doEndChangeSet(name);
  }

  private boolean doEndChangeSet(String name) {
    if (myCurrentChangeSet.getChanges().isEmpty()) {
      myCurrentBlock.removeLast();
      return false;
    }

    myCurrentChangeSet.setName(name);
    myCurrentChangeSet = null;

    flushChanges(false);
    return true;
  }

  @TestOnly
  public List<ChangeSet> getChangesInTests() {
    List<ChangeSet> result = new ArrayList<ChangeSet>();
    for (ChangeSet each : iterChanges()) {
      result.add(each);
    }
    return result;
  }

  public synchronized Iterable<ChangeSet> iterChanges() {
    return new Iterable<ChangeSet>() {
      public Iterator<ChangeSet> iterator() {
        return new Iterator<ChangeSet>() {
          private ChangeSetBlock currentBlock;
          private Iterator<ChangeSet> currentIter;

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
                currentBlock = myCurrentBlock;
                List<ChangeSet> copy = new ArrayList<ChangeSet>(currentBlock.changes);
                currentIter = ContainerUtil.iterateBackward(copy).iterator();
              }
            }
            while (!currentIter.hasNext()) {
              synchronized (ChangeList.this) {
                currentBlock = myStorage.readPrevious(currentBlock);
              }
              if (currentBlock == null) return null;
              currentIter = ContainerUtil.iterateBackward(currentBlock.changes).iterator();
            }
            return currentIter.next();
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  private void flushChanges(boolean force) {
    if (myChangeSetDepth > 0) return;
    if (myCurrentBlock.shouldFlush(force) || flushEveryChangeSetInTests()) {
      myStorage.writeNextBlock(myCurrentBlock);
      myCurrentBlock = myStorage.createNewBlock();
    }
    myStorage.flush();
  }

  private boolean flushEveryChangeSetInTests() {
    Application app = ApplicationManager.getApplication();
    return app == null || app.isUnitTestMode();
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
    myStorage.purge(period, myIntervalBetweenActivities, new Consumer<ChangeSet>() {
      public void consume(ChangeSet changeSet) {
        for (Content each : changeSet.getContentsToPurge()) {
          each.release();
        }
      }
    });
  }

  @TestOnly
  public void setIntervalBetweenActivities(int value) {
    myIntervalBetweenActivities = value;
  }
}