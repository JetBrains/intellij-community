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
import com.intellij.history.core.changes.ChangeList;
import com.intellij.history.core.revisions.*;
import com.intellij.history.core.tree.Entry;

import java.util.ArrayList;
import java.util.List;

public class RevisionsCollector extends ChangeSetsProcessor {
  private final Entry myRoot;
  private final ChangeList myChangeList;

  private final List<Revision> myResult = new ArrayList<Revision>();

  public RevisionsCollector(LocalVcs vcs, String path, Entry rootEntry, ChangeList cl) {
    super(vcs, path);

    myRoot = rootEntry;
    myChangeList = cl;
  }

  public List<Revision> getResult() {
    process();
    return myResult;
  }

  @Override
  protected List<Change> collectChanges() {
    return myChangeList.getChangesFor(myRoot, myPath);
  }

  @Override
  protected void nothingToVisit() {
    myResult.add(new CurrentRevision(myEntry));
  }

  @Override
  protected void visitLabel(Change c) {
    myResult.add(new LabeledRevision(myEntry, myRoot, myChangeList, c));
  }

  @Override
  protected void visitRegular(Change c) {
    myResult.add(new RevisionAfterChange(myEntry, myRoot, myChangeList, c));
  }

  @Override
  protected void visitFirstAvailableNonCreational(Change c) {
    myResult.add(new RevisionBeforeChange(myEntry, myRoot, myChangeList, c));
  }
}
