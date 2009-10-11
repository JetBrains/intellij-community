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
import com.intellij.history.core.changes.ChangeList;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.Entry;

public class RevisionBeforeChange extends Revision {
  protected Entry myEntry;
  protected Entry myRoot;
  protected ChangeList myChangeList;
  protected Change myChange;

  public RevisionBeforeChange(Entry e, Entry r, ChangeList cl, Change c) {
    myEntry = e;
    myRoot = r;
    myChangeList = cl;
    myChange = c;
  }

  @Override
  public long getTimestamp() {
    return myChange.getTimestamp();
  }

  @Override
  public Entry getEntry() {
    Entry rootCopy = myRoot.copy();
    myChangeList.revertUpTo(rootCopy, myChange, includeMyChange());
    return rootCopy.getEntry(myEntry.getId());
  }

  @Override
  public boolean isBefore(ChangeSet c) {
    return myChangeList.isBefore(myChange, c, includeMyChange());
  }

  protected boolean includeMyChange() {
    return true;
  }
}