// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class HistoryDialogModel {
  protected final Project myProject;
  protected final LocalHistoryFacade myVcs;
  protected final VirtualFile myFile;
  protected final IdeaGateway myGateway;
  private String myFilter;
  protected boolean myBefore = true;

  private RevisionData myRevisionData;

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

  public @NlsContexts.DialogTitle String getTitle() {
    return FileUtil.toSystemDependentName(myFile.getPath());
  }

  @ApiStatus.Internal
  protected @NotNull RevisionData getRevisionData() {
    if (myRevisionData == null) {
      myRevisionData = collectRevisionData();
    }
    return myRevisionData;
  }

  public @NotNull List<RevisionItem> getRevisions() {
    return getRevisionData().getRevisions();
  }

  public @NotNull Revision getCurrentRevision() {
    return getRevisionData().getCurrentRevision();
  }

  @ApiStatus.Internal
  protected @NotNull RevisionData collectRevisionData() {
    return RevisionDataKt.collectRevisionData(myProject, myGateway, myVcs, createRootEntry(), myFile, myFilter, myBefore);
  }

  protected @NotNull RootEntry createRootEntry() {
    return ReadAction.compute(() -> myGateway.createTransientRootEntry());
  }

  public void processContents(@NotNull PairProcessor<? super Revision, ? super String> processor) {
    RevisionDataKt.processContents(myVcs, myGateway, myFile, ContainerUtil.map(getRevisions(), item -> item.revision), myBefore, processor);
  }

  public @Nullable String myFilter() {
    return myFilter;
  }

  public void setFilter(@Nullable String filter) {
    myFilter = StringUtil.isEmptyOrSpaces(filter) ? null : filter;
    clearRevisions();
  }

  public void clearRevisions() {
    myRevisionData = null;
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

  protected @Nullable Entry getLeftEntry() {
    if (myLeftEntryCache == null) {
      // array is used because entry itself can be null
      myLeftEntryCache = new Entry[]{getLeftRevision().findEntry()};
    }
    return myLeftEntryCache[0];
  }

  protected @Nullable Entry getRightEntry() {
    if (myRightEntryCache == null) {
      // array is used because entry itself can be null
      myRightEntryCache = new Entry[]{getRightRevision().findEntry()};
    }
    return myRightEntryCache[0];
  }

  public boolean selectRevisions(int first, int second) {
    int l, r;
    if (first == second) {
      r = -1;
      l = first == -1 ? 0 : first;
    }
    else {
      r = first;
      l = second;
    }
    if (myRightRevisionIndex == r && myLeftRevisionIndex == l) {
      return false;
    }
    myRightRevisionIndex = r;
    myLeftRevisionIndex = l;
    resetEntriesCache();
    return true;
  }

  public boolean resetSelection() {
    return selectRevisions(0, 0);
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
    return Revision.getDifferencesBetween(getLeftRevision(), getRightRevision());
  }

  protected Change createChange(@NotNull Difference d) {
    return new Change(d.getLeftContentRevision(myGateway), d.getRightContentRevision(myGateway));
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

  @ApiStatus.Internal
  public abstract @NotNull LocalHistoryCounter.Kind getKind();
}
