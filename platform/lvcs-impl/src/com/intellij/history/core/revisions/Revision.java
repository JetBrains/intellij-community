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

package com.intellij.history.core.revisions;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.Entry;

import java.util.List;

public abstract class Revision {
  public String getName() {
    return null;
  }

  public abstract long getTimestamp();

  public String getCauseChangeName() {
    return getCauseChange() == null ? null : getCauseChange().getName();
  }

  public Change getCauseChange() {
    return null;
  }

  public abstract Entry getEntry();

  public List<Difference> getDifferencesWith(Revision right) {
    Entry leftEntry = getEntry();
    Entry rightEntry = right.getEntry();
    return leftEntry.getDifferencesWith(rightEntry);
  }

  public boolean isImportant() {
    return true;
  }

  public boolean isBefore(ChangeSet c) {
    return false;
  }
}
