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
import com.intellij.history.core.tree.RootEntry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.patches.PatchCreator;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public abstract class HistoryDialogModel {
  protected final Project myProject;
  protected LocalHistoryFacade myVcs;
  protected VirtualFile myFile;
  protected IdeaGateway myGateway;
  private String myFilter;
  private List<RevisionItem> myRevisionsCache;
  private Revision myCurrentRevisionCache;
  private int myRightRevisionIndex;
  private int myLeftRevisionIndex;
  private Entry[] myLeftEntryCache;
  private Entry[] myRightEntryCache;

  public HistoryDialogModel(Project p, IdeaGateway gw, LocalHistoryFacade vcs, VirtualFile f) {
    myProject = p;
    myVcs = vcs;
    myFile = f;
    myGateway = gw;
  }

  public String getTitle() {
    return FileUtil.toSystemDependentName(myFile.getPath());
  }


  public List<RevisionItem> getRevisions() {
    if (myRevisionsCache == null) {
      Pair<Revision, List<RevisionItem>> revs = calcRevisionsCache();
      myCurrentRevisionCache = revs.first;
      myRevisionsCache = revs.second;
    }
    return myRevisionsCache;
  }

  public Revision getCurrentRevision() {
    getRevisions();
    return myCurrentRevisionCache;
  }

  protected Pair<Revision, List<RevisionItem>> calcRevisionsCache() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Pair<Revision, List<RevisionItem>>>() {
      public Pair<Revision, List<RevisionItem>> compute() {
        myGateway.registerUnsavedDocuments(myVcs);
        String path = myFile.getPath();
        RootEntry root = myGateway.createTransientRootEntry();
        RevisionsCollector collector = new RevisionsCollector(myVcs, root, path, myProject.getLocationHash(), myFilter);

        List<Revision> all = collector.getResult();
        return Pair.create(all.get(0), groupRevisions(all.subList(1, all.size())));
      }
    });
  }

  private List<RevisionItem> groupRevisions(List<Revision> revs) {
    LinkedList<RevisionItem> result = new LinkedList<>();

    for (Revision each : ContainerUtil.iterateBackward(revs)) {
      if (each.isLabel() && !result.isEmpty()) {
        result.getFirst().labels.addFirst(each);
      } else {
        result.addFirst(new RevisionItem(each));
      }
    }

    return result;
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
    if (getRevisions().isEmpty()) return getCurrentRevision();
    return getRevisions().get(myLeftRevisionIndex).revision;
  }

  public Revision getRightRevision() {
    if (isCurrentRevisionSelected() || getRevisions().isEmpty()) {
      return getCurrentRevision();
    }
    return getRevisions().get(myRightRevisionIndex).revision;
  }

  protected Entry getLeftEntry() {
    if (myLeftEntryCache == null) {
      // array is used because entry itself can be null
      myLeftEntryCache = new Entry[]{getLeftRevision().findEntry()};
    }
    return myLeftEntryCache[0];
  }

  protected Entry getRightEntry() {
    if (myRightEntryCache == null) {
      // array is used because entry itself can be null
      myRightEntryCache = new Entry[]{getRightRevision().findEntry()};
    }
    return myRightEntryCache[0];
  }

  public void selectRevisions(int first, int second) {
    if (first == second) {
      myRightRevisionIndex = -1;
      myLeftRevisionIndex = first == -1 ? 0 : first;
    }
    else {
      myRightRevisionIndex = first;
      myLeftRevisionIndex = second;
    }
    resetEntriesCache();
  }

  public void resetSelection() {
    selectRevisions(0, 0);
  }

  public boolean isCurrentRevisionSelected() {
    return myRightRevisionIndex == -1;
  }

  public List<Change> getChanges() {
    List<Difference> dd = getDifferences();

    List<Change> result = new ArrayList<>();
    for (Difference d : dd) {
      result.add(createChange(d));
    }

    return result;
  }

  protected List<Difference> getDifferences() {
    return getLeftRevision().getDifferencesWith(getRightRevision());
  }

  protected Change createChange(Difference d) {
    return new Change(d.getLeftContentRevision(myGateway), d.getRightContentRevision(myGateway));
  }

  public void createPatch(String path, String basePath, boolean isReverse, @NotNull Charset charset) throws VcsException, IOException {
    PatchCreator.create(myProject, basePath, getChanges(), path, isReverse, null, charset);
  }

  public abstract Reverter createReverter();

  public boolean isRevertEnabled() {
    return isCorrectSelectionForRevertAndPatch();
  }

  public boolean isCreatePatchEnabled() {
    return isCorrectSelectionForRevertAndPatch();
  }

  private boolean isCorrectSelectionForRevertAndPatch() {
    return myLeftRevisionIndex != -1;
  }

  public boolean canPerformCreatePatch() {
    return !getLeftEntry().hasUnavailableContent() && !getRightEntry().hasUnavailableContent();
  }
}
