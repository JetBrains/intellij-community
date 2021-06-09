// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.Content;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.RevisionsCollector;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

  @NlsContexts.DialogTitle
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
    return ReadAction.compute(() -> {
      myGateway.registerUnsavedDocuments(myVcs);
      String path = myGateway.getPathOrUrl(myFile);
      RootEntry root = myGateway.createTransientRootEntry();
      RevisionsCollector collector = new RevisionsCollector(myVcs, root, path, myProject.getLocationHash(), myFilter);

      List<Revision> all = collector.getResult();
      return Pair.create(all.get(0), groupRevisions(all.subList(1, all.size())));
    });
  }

  public void processContents(@NotNull PairProcessor<? super Revision, ? super String> processor) {
    Map<Long, Revision> revMap = new HashMap<>();
    for (RevisionItem r : getRevisions()) {
      revMap.put(r.revision.getChangeSetId(), r.revision);
    }
    if (revMap.isEmpty()) return;
    myVcs.accept(new ChangeVisitor() {
      final RootEntry root = ContainerUtil.getFirstItem(revMap.values()).getRoot().copy();
      String path = myGateway.getPathOrUrl(myFile);
      {
        processContent(revMap.get(null));
      }

      private boolean processContent(@Nullable Revision revision) {
        if (revision == null) return true;
        Entry entry = root.findEntry(path);
        Content content = entry == null ? null : entry.getContent();
        String text = content == null ? null : content.getString(entry, myGateway);
        return processor.process(revision, text);
      }

      @Override
      public void begin(ChangeSet c) {
        ProgressManager.checkCanceled();
        if (Thread.currentThread().isInterrupted()) {
          throw new ProcessCanceledException();
        }
      }

      @Override
      public void end(ChangeSet c) throws StopVisitingException {
        if (!processContent(revMap.get(c.getId()))) {
          stop();
        }
      }

      @Override
      public void visit(StructuralChange c) {
        if (c.affectsPath(path)) {
          c.revertOn(root, false);
          path = c.revertPath(path);
        }
      }
    });
  }

  private static List<RevisionItem> groupRevisions(List<? extends Revision> revs) {
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
    return getLeftRevision().getDifferencesWith(getRightRevision());
  }

  protected Change createChange(Difference d) {
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
}
