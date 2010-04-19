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

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;

public class RevisionBeforeChange extends Revision {
  private final LocalHistoryFacade myFacade;
  private final RootEntry myRoot;
  private final String myEntryPath;
  private final long myTimestamp;
  private final Change myChangeToRevert;

  public RevisionBeforeChange(LocalHistoryFacade facade, RootEntry r, String entryPath, ChangeSet changeSet) {
    myFacade = facade;
    myRoot = r;
    myEntryPath = entryPath;

    myTimestamp = changeSet.getTimestamp();
    myChangeToRevert = revertThisChangeSet()
                      ? changeSet.getFirstChange()
                      : changeSet.getLastChange();
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public Entry getEntry() {
    RootEntry rootCopy = myRoot.copy();

    boolean revertThis = revertThisChangeSet();
    String path = myFacade.revertUpTo(rootCopy, myEntryPath, null, myChangeToRevert, revertThis);

    return rootCopy.getEntry(path);
  }

  protected boolean revertThisChangeSet() {
    return true;
  }

  public String toString() {
    return getClass().getSimpleName() + ": " + myChangeToRevert;
  }
}