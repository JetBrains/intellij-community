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

import java.util.ArrayList;
import java.util.List;

public class ChangeSetBlock {
  private static final int BLOCK_SIZE = 1000;
  public int id;
  public final List<ChangeSet> changes;

  public ChangeSetBlock(int id) {
    this.id = id;
    this.changes = new ArrayList<ChangeSet>(BLOCK_SIZE);
  }

  ChangeSetBlock(int id, List<ChangeSet> changes) {
    this.id = id;
    this.changes = changes;
  }

  public void add(ChangeSet changeSet) {
    changes.add(changeSet);
  }

  public void removeLast() {
    changes.remove(changes.size() - 1);
  }

  public boolean shouldFlush(boolean force) {
    int count = 0;
    for (ChangeSet each : changes) {
      count += each.getChanges().size();

      if (count >= BLOCK_SIZE) return true;
      if (force && count > 0) return true;
    }
    return false;
  }
}
