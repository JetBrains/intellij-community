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
import com.intellij.history.core.Paths;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;

import java.util.List;

public class RevisionAfterChange extends RevisionBeforeChange {
  private final long myId;
  private final String myName;
  private final String myLabel;
  private final int myLabelColor;
  private final Pair<List<String>, Integer> myAffectedFiles;

  public RevisionAfterChange(LocalHistoryFacade facade, RootEntry r, String entryPath, ChangeSet changeSet) {
    super(facade, r, entryPath, changeSet);

    // do not store changeSet to prevent huge memory consumption
    myId = changeSet.getId();
    myLabel = changeSet.getLabel();
    myLabelColor = changeSet.getLabelColor();
    myName = changeSet.getName();

    List<String> allAffectedFiles = changeSet.getAffectedPaths();
    List<String> someAffectedFiles = new SmartList<String>();
    for (String each : allAffectedFiles.subList(0, Math.min(3, allAffectedFiles.size()))) {
      someAffectedFiles.add(Paths.getNameOf(each));
    }
    myAffectedFiles = Pair.create(someAffectedFiles, allAffectedFiles.size());
  }

  @Override
  public String getLabel() {
    return myLabel;
  }

  @Override
  public int getLabelColor() {
    return myLabelColor;
  }

  @Override
  public Long getChangeSetId() {
    return myId;
  }

  @Override
  public String getChangeSetName() {
    return myName;
  }

  @Override
  public Pair<List<String>, Integer> getAffectedFileNames() {
    return myAffectedFiles;
  }

  @Override
  protected boolean revertThisChangeSet() {
    return false;
  }
}
