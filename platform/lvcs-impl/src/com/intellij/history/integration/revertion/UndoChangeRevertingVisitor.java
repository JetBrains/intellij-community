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

package com.intellij.history.integration.revertion;

import com.intellij.history.LocalHistory;
import com.intellij.history.core.Content;
import com.intellij.history.core.Paths;
import com.intellij.history.core.changes.*;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.command.impl.DocumentUndoProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UndoChangeRevertingVisitor extends ChangeVisitor {
  private final IdeaGateway myGateway;
  private final Set<DelayedApply> myDelayedApplies = new HashSet<>();

  private final long myFromChangeId;
  private final long myToChangeId;

  private boolean isReverting;

  public UndoChangeRevertingVisitor(IdeaGateway gw, @NotNull Long fromChangeId, @Nullable Long toChangeId) {
    myGateway = gw;
    myFromChangeId = fromChangeId;
    myToChangeId = toChangeId == null ? -1 : toChangeId;
  }

  protected boolean shouldRevert(Change c) {
    if (c.getId() == myFromChangeId) {
      isReverting = true;
    }
    return isReverting && !(c instanceof ContentChange);
  }

  protected void checkShouldStop(Change c) throws StopVisitingException {
    if (c.getId() == myToChangeId) stop();
  }

  @Override
  public void visit(CreateEntryChange c) throws StopVisitingException {
    if (shouldRevert(c)) {
      VirtualFile f = myGateway.findVirtualFile(c.getPath());
      if (f != null) {
        unregisterDelayedApplies(f);
        try {
          f.delete(LocalHistory.VFS_EVENT_REQUESTOR);
        }
        catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      }
    }
    checkShouldStop(c);
  }

  @Override
  public void visit(ContentChange c) throws StopVisitingException {
    if (shouldRevert(c)) {
      try {
        VirtualFile f = myGateway.findOrCreateFileSafely(c.getPath(), false);
        registerDelayedContentApply(f, c.getOldContent(), c.getOldTimestamp());
      }
      catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }
    checkShouldStop(c);
  }

  @Override
  public void visit(RenameChange c) throws StopVisitingException {
    if (shouldRevert(c)) {
      VirtualFile f = myGateway.findVirtualFile(c.getPath());
      if (f != null) {
        VirtualFile existing = f.getParent().findChild(c.getOldName());
        try {
          if (existing != null && !Comparing.equal(existing, f)) {
            existing.delete(LocalHistory.VFS_EVENT_REQUESTOR);
          }
          f.rename(LocalHistory.VFS_EVENT_REQUESTOR, c.getOldName());
        }
        catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      }
    }
    checkShouldStop(c);
  }

  @Override
  public void visit(ROStatusChange c) throws StopVisitingException {
    if (shouldRevert(c)) {
      VirtualFile f = myGateway.findVirtualFile(c.getPath());
      if (f != null) {
        registerDelayedROStatusApply(f, c.getOldStatus());
      }
    }
    checkShouldStop(c);
  }

  @Override
  public void visit(MoveChange c) throws StopVisitingException {
    if (shouldRevert(c)) {
      VirtualFile f = myGateway.findVirtualFile(c.getPath());
      if (f != null) {
        try {
          VirtualFile parent = myGateway.findOrCreateFileSafely(c.getOldParent(), true);
          VirtualFile existing = parent.findChild(f.getName());
          if (existing != null) existing.delete(LocalHistory.VFS_EVENT_REQUESTOR);
          f.move(LocalHistory.VFS_EVENT_REQUESTOR, parent);
        }
        catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      }
    }
    checkShouldStop(c);
  }

  @Override
  public void visit(DeleteChange c) throws StopVisitingException {
    if (shouldRevert(c)) {
      try {
        VirtualFile parent = myGateway.findOrCreateFileSafely(Paths.getParentOf(c.getPath()), true);
        revertDeletion(parent, c.getDeletedEntry());
      }
      catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }
    checkShouldStop(c);
  }

  private void revertDeletion(VirtualFile parent, Entry e) throws IOException {
    VirtualFile f = myGateway.findOrCreateFileSafely(parent, e.getName(), e.isDirectory());
    if (e.isDirectory()) {
      for (Entry child : e.getChildren()) revertDeletion(f, child);
    }
    else {
      registerDelayedContentApply(f, e.getContent(), e.getTimestamp());
      registerDelayedROStatusApply(f, e.isReadOnly());
    }
  }

  private void registerDelayedContentApply(VirtualFile f, Content content, long timestamp) {
    registerDelayedApply(new DelayedContentApply(f, content, timestamp));
  }

  private void registerDelayedROStatusApply(VirtualFile f, boolean isReadOnly) {
    registerDelayedApply(new DelayedROStatusApply(f, isReadOnly));
  }

  private void registerDelayedApply(DelayedApply a) {
    myDelayedApplies.remove(a);
    myDelayedApplies.add(a);
  }

  private void unregisterDelayedApplies(VirtualFile fileOrDir) {
    List<DelayedApply> toRemove = new ArrayList<>();

    for (DelayedApply a : myDelayedApplies) {
      if (VfsUtil.isAncestor(fileOrDir, a.getFile(), false)) {
        toRemove.add(a);
      }
    }

    for (DelayedApply a : toRemove) {
      myDelayedApplies.remove(a);
    }
  }

  @Override
  public void finished() {
    try {
      for (DelayedApply a : myDelayedApplies) a.apply();
    }
    catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private static abstract class DelayedApply {
    protected VirtualFile myFile;

    protected DelayedApply(VirtualFile f) {
      myFile = f;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public abstract void apply() throws IOException;

    @Override
    public boolean equals(Object o) {
      if (!getClass().equals(o.getClass())) return false;
      return myFile.equals(((DelayedApply)o).myFile);
    }

    @Override
    public int hashCode() {
      return getClass().hashCode() + 32 * myFile.hashCode();
    }
  }

  private static class DelayedContentApply extends DelayedApply {
    private final Content myContent;
    private final long myTimestamp;

    public DelayedContentApply(VirtualFile f, Content content, long timestamp) {
      super(f);
      myContent = content;
      myTimestamp = timestamp;
    }

    @Override
    public void apply() throws IOException {
      if (!myContent.isAvailable()) return;

      boolean isReadOnly = !myFile.isWritable();
      ReadOnlyAttributeUtil.setReadOnlyAttribute(myFile, false);

      Document doc = FileDocumentManager.getInstance().getCachedDocument(myFile);
      DocumentUndoProvider.startDocumentUndo(doc);
      try {
        myFile.setBinaryContent(myContent.getBytes(), -1, myTimestamp);
      }
      finally {
        DocumentUndoProvider.finishDocumentUndo(doc);
      }

      ReadOnlyAttributeUtil.setReadOnlyAttribute(myFile, isReadOnly);
    }
  }

  private static class DelayedROStatusApply extends DelayedApply {
    private final boolean isReadOnly;

    private DelayedROStatusApply(VirtualFile f, boolean isReadOnly) {
      super(f);
      this.isReadOnly = isReadOnly;
    }

    public void apply() throws IOException {
      ReadOnlyAttributeUtil.setReadOnlyAttribute(myFile, isReadOnly);
    }
  }

  public static class RuntimeIOException extends RuntimeException {
    public RuntimeIOException(Throwable cause) {
      super(cause);
    }
  }
}
