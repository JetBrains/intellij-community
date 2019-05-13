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

import com.intellij.history.ByteContent;
import com.intellij.history.core.changes.*;
import com.intellij.history.core.revisions.ChangeRevision;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;

public class LocalHistoryFacade {
  private final ChangeList myChangeList;
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public LocalHistoryFacade(ChangeList changeList) {
    myChangeList = changeList;
  }

  public void beginChangeSet() {
    myChangeList.beginChangeSet();
  }

  public void forceBeginChangeSet() {
    if (myChangeList.forceBeginChangeSet()) {
      fireChangeSetFinished();
    }
  }

  public void endChangeSet(String name) {
    if (myChangeList.endChangeSet(name)) {
      fireChangeSetFinished();
    }
  }

  public void created(String path, boolean isDirectory) {
    addChange(isDirectory ? new CreateDirectoryChange(myChangeList.nextId(), path)
                          : new CreateFileChange(myChangeList.nextId(), path));
  }

  public void contentChanged(String path, Content oldContent, long oldTimestamp) {
    addChange(new ContentChange(myChangeList.nextId(), path, oldContent, oldTimestamp));
  }

  public void renamed(String path, String oldName) {
    addChange(new RenameChange(myChangeList.nextId(), path, oldName));
  }

  public void readOnlyStatusChanged(String path, boolean oldStatus) {
    addChange(new ROStatusChange(myChangeList.nextId(), path, oldStatus));
  }

  public void moved(String path, String oldParent) {
    addChange(new MoveChange(myChangeList.nextId(), path, oldParent));
  }

  public void deleted(String path, Entry deletedEntry) {
    addChange(new DeleteChange(myChangeList.nextId(), path, deletedEntry));
  }

  public LabelImpl putSystemLabel(String name, String projectId, int color) {
    return putLabel(new PutSystemLabelChange(myChangeList.nextId(), name, projectId, color));
  }

  public LabelImpl putUserLabel(String name, String projectId) {
    return putLabel(new PutLabelChange(myChangeList.nextId(), name, projectId));
  }

  private void addChange(@NotNull Change c) {
    beginChangeSet();
    myChangeList.addChange(c);
    fireChangeAdded(c);
    endChangeSet(null);
  }

  @TestOnly
  public void addChangeInTests(@NotNull StructuralChange c) {
    addChange(c);
  }

  private LabelImpl putLabel(@NotNull final PutLabelChange c) {
    addChange(c);
    return new LabelImpl() {

      @Override
      public long getLabelChangeId() {
        return c.getId();
      }

      @Override
      public ByteContent getByteContent(RootEntry root, String path) {
        return getByteContentBefore(root, path, c);
      }
    };
  }

  @TestOnly
  public void putLabelInTests(final PutLabelChange c) {
    putLabel(c);
  }

  @TestOnly
  public ChangeList getChangeListInTests() {
    return myChangeList;
  }

  private ByteContent getByteContentBefore(RootEntry root, String path, Change change) {
    root = root.copy();
    String newPath = revertUpTo(root, path, null, change, false, false);
    Entry entry = root.findEntry(newPath);
    if (entry == null) return new ByteContent(false, null);
    if (entry.isDirectory()) return new ByteContent(true, null);

    return new ByteContent(false, entry.getContent().getBytesIfAvailable());
  }

  public List<RecentChange> getRecentChanges(RootEntry root) {
    List<RecentChange> result = new ArrayList<>();

    for (ChangeSet c : myChangeList.iterChanges()) {
      if (c.isContentChangeOnly()) continue;
      if (c.isLabelOnly()) continue;
      if (c.getName() == null) continue;

      Revision before = new ChangeRevision(this, root, "", c, true);
      Revision after = new ChangeRevision(this, root, "", c, false);
      result.add(new RecentChange(before, after));
      if (result.size() >= 20) break;
    }

    return result;
  }

  public void accept(ChangeVisitor v) {
    myChangeList.accept(v);
  }

  public String revertUpTo(@NotNull final RootEntry root,
                           @NotNull String path,
                           final ChangeSet targetChangeSet,
                           final Change targetChange,
                           final boolean revertTargetChange,
                           final boolean warnOnFileNotFound) {
    final String[] result = {path};
    myChangeList.accept(new ChangeVisitor() {
      @Override
      public void begin(ChangeSet c) throws StopVisitingException {
        if (!revertTargetChange && c.equals(targetChangeSet)) stop();
      }

      @Override
      public void end(ChangeSet c) throws StopVisitingException {
        if (c.equals(targetChangeSet)) stop();
      }

      @Override
      public void visit(PutLabelChange c) throws StopVisitingException {
        if (c.equals(targetChange)) stop();
      }

      @Override
      public void visit(StructuralChange c) throws StopVisitingException {
        if (!revertTargetChange && c.equals(targetChange)) stop();

        c.revertOn(root, warnOnFileNotFound);
        result[0] = c.revertPath(result[0]);

        if (c.equals(targetChange)) stop();
      }
    });

    return result[0];
  }

  public void addListener(@NotNull final Listener l, @Nullable Disposable parent) {
    myListeners.add(l);

    if (parent != null) {
      Disposer.register(parent, new Disposable() {
        @Override
        public void dispose() {
          myListeners.remove(l);
        }
      });
    }
  }

  public void removeListener(@NotNull Listener l) {
    myListeners.remove(l);
  }

  private void fireChangeAdded(Change c) {
    for (Listener each : myListeners) {
      each.changeAdded(c);
    }
  }

  private void fireChangeSetFinished() {
    for (Listener each : myListeners) {
      each.changeSetFinished();
    }
  }

  public abstract static class Listener {
    public void changeAdded(Change c) {
    }

    public void changeSetFinished() {
    }
  }
}
