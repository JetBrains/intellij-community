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

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.RevisionsCollector;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.patches.PatchCreator;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class HistoryDialogModel {
  protected final Project myProject;
  protected LocalHistoryFacade myVcs;
  protected VirtualFile myFile;
  protected IdeaGateway myGateway;
  private String myFilter;
  private List<Revision> myRevisionsCache;
  private int myRightRevisionIndex;
  private int myLeftRevisionIndex;
  private Entry[] myLeftEntryCache;
  private Entry[] myRightEntryCache;

  public HistoryDialogModel(Project p,  IdeaGateway gw, LocalHistoryFacade vcs, VirtualFile f) {
    myProject = p;
    myVcs = vcs;
    myFile = f;
    myGateway = gw;
  }

  public String getTitle() {
    return FileUtil.toSystemDependentName(myFile.getPath());
  }

  public List<Revision> getRevisions() {
    if (myRevisionsCache == null) {
      myRevisionsCache = calcRevisionsCache();
    }
    return myRevisionsCache;
  }

  protected List<Revision> calcRevisionsCache() {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<Revision>>() {
      public List<Revision> compute() {
        myGateway.registerUnsavedDocuments(myVcs);
        RevisionsCollector collector = new RevisionsCollector(myVcs, myGateway.createTransientRootEntry(), 
                                                              myFile.getPath(), myProject.getLocationHash(), myFilter);
        return collector.getResult();
      }
    });
  }

  public void setFilter(@Nullable String filter) {
    myFilter = StringUtil.isEmptyOrSpaces(filter) ? null : filter;
    clearRevisions();
  }

  public void clearRevisions() {
    myRevisionsCache = null;
    resetEntriesCache();
  }

  private void resetEntriesCache() {
    myLeftEntryCache = null;
    myRightEntryCache = null;
  }

  public Revision getLeftRevision() {
    return getRevisions().get(myLeftRevisionIndex);
  }

  public Revision getRightRevision() {
    return getRevisions().get(myRightRevisionIndex);
  }

  protected Entry getLeftEntry() {
    if (myLeftEntryCache == null) {
      // array is used because entry itself can be null
      myLeftEntryCache = new Entry[]{getLeftRevision().getEntry()};
    }
    return myLeftEntryCache[0];
  }

  protected Entry getRightEntry() {
    if (myRightEntryCache == null) {
      // array is used because entry itself can be null
      myRightEntryCache = new Entry[]{getRightRevision().getEntry()};
    }
    return myRightEntryCache[0];
  }

  public void selectRevisions(int first, int second) {
    doSelect(first, second);
    resetEntriesCache();
  }

  private void doSelect(int first, int second) {
    if (first == second) {
      myRightRevisionIndex = 0;
      myLeftRevisionIndex = first == -1 ? 0 : first;
    }
    else {
      myRightRevisionIndex = first;
      myLeftRevisionIndex = second;
    }
  }

  public void resetSelection() {
    selectRevisions(0, 0);
  }

  public boolean isCurrentRevisionSelected() {
    return myRightRevisionIndex == 0;
  }

  public List<Change> getChanges() {
    List<Difference> dd = getLeftRevision().getDifferencesWith(getRightRevision());

    List<Change> result = new ArrayList<Change>();
    for (Difference d : dd) {
      result.add(createChange(d));
    }

    return result;
  }

  protected Change createChange(Difference d) {
    return new Change(d.getLeftContentRevision(myGateway), d.getRightContentRevision(myGateway));
  }

  public void createPatch(String path, boolean isReverse) throws VcsException, IOException {
    PatchCreator.create(myProject, getChanges(), path, isReverse);
  }

  public abstract Reverter createReverter();

  public boolean isRevertEnabled() {
    return isCorrectSelectionForRevertAndPatch();
  }

  public boolean isCreatePatchEnabled() {
    return isCorrectSelectionForRevertAndPatch();
  }

  private boolean isCorrectSelectionForRevertAndPatch() {
    return myLeftRevisionIndex > 0;
  }

  public boolean canPerformCreatePatch() {
    return !getLeftEntry().hasUnavailableContent() && !getRightEntry().hasUnavailableContent();
  }
}
