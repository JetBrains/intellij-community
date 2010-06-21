/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.util.Consumer;

import java.util.ArrayList;
import java.util.List;

public class InMemoryChangeListStorage implements ChangeListStorage {
  private int myCurrentId;
  private List<ChangeSetBlock> myBlocks = new ArrayList<ChangeSetBlock>();

  public void close() {
  }

  public long nextId() {
    return myCurrentId++;
  }

  public ChangeSetBlock createNewBlock() {
    return new ChangeSetBlock(-1);
  }

  public ChangeSetBlock readPrevious(ChangeSetBlock block) {
    if (myBlocks.isEmpty()) return null;
    if (block.id == 0) return null;
    if (block.id == -1) return myBlocks.get(myBlocks.size() - 1);
    return myBlocks.get(block.id - 1);
  }

  public void writeNextBlock(ChangeSetBlock block) {
    myBlocks.add(block);
    block.id = myBlocks.size() - 1;
  }

  public void purge(long period, int intervalBetweenActivities, Consumer<ChangeSet> processor) {
    throw new UnsupportedOperationException();
  }

  public void flush() {
  }
}
