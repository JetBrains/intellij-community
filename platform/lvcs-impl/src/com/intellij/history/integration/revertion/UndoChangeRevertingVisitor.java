// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.revertion;

import com.intellij.history.LocalHistory;
import com.intellij.history.core.Content;
import com.intellij.history.core.Paths;
import com.intellij.history.core.changes.*;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.command.impl.DocumentUndoProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class UndoChangeRevertingVisitor extends ChangeVisitor {
  private static final Logger LOG = Logger.getInstance(UndoChangeRevertingVisitor.class);

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

  private boolean shouldRevert(@NotNull Change c) {
    if (c.getId() == myFromChangeId) {
      isReverting = true;
    }
    boolean shouldRevert = isReverting && !(c instanceof ContentChange);
    if (shouldRevert && LOG.isDebugEnabled()) {
      LOG.debug("Reverting " + c);
    }
    return shouldRevert;
  }

  private void checkShouldStop(@NotNull Change c) throws StopVisitingException {
    if (c.getId() == myToChangeId) stop();
  }

  @Override
  public void visit(@NotNull CreateEntryChange c) throws StopVisitingException {
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
  public void visit(@NotNull ContentChange c) throws StopVisitingException {
    if (shouldRevert(c)) {
      try {
        VirtualFile f = myGateway.findOrCreateFileSafely(c.getPath(), false);
        registerDelayedContentApply(f, c.getOldContent());
      }
      catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }
    checkShouldStop(c);
  }

  @Override
  public void visit(@NotNull RenameChange c) throws StopVisitingException {
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
  public void visit(@NotNull ROStatusChange c) throws StopVisitingException {
    if (shouldRevert(c)) {
      VirtualFile f = myGateway.findVirtualFile(c.getPath());
      if (f != null) {
        registerDelayedROStatusApply(f, c.getOldStatus());
      }
    }
    checkShouldStop(c);
  }

  @Override
  public void visit(@NotNull MoveChange c) throws StopVisitingException {
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
  public void visit(@NotNull DeleteChange c) throws StopVisitingException {
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

  private void revertDeletion(VirtualFile parent, @NotNull Entry e) throws IOException {
    VirtualFile f = myGateway.findOrCreateFileSafely(parent, e.getName(), e.isDirectory());
    if (e.isDirectory()) {
      for (Entry child : e.getChildren()) revertDeletion(f, child);
    }
    else {
      registerDelayedContentApply(f, e.getContent());
      registerDelayedROStatusApply(f, e.isReadOnly());
    }
  }

  private void registerDelayedContentApply(VirtualFile f, Content content) {
    registerDelayedApply(new DelayedContentApply(f, content));
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

  private abstract static class DelayedApply {
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

  private static final class DelayedContentApply extends DelayedApply {
    private final Content myContent;

    DelayedContentApply(VirtualFile f, Content content) {
      super(f);
      myContent = content;
    }

    @Override
    public void apply() throws IOException {
      if (!myContent.isAvailable()) return;

      boolean isReadOnly = !myFile.isWritable();
      ReadOnlyAttributeUtil.setReadOnlyAttribute(myFile, false);

      Document doc = FileDocumentManager.getInstance().getCachedDocument(myFile);
      DocumentUndoProvider.startDocumentUndo(doc);
      try {
        myFile.setBinaryContent(myContent.getBytes());
      }
      finally {
        DocumentUndoProvider.finishDocumentUndo(doc);
      }

      ReadOnlyAttributeUtil.setReadOnlyAttribute(myFile, isReadOnly);
    }
  }

  private static final class DelayedROStatusApply extends DelayedApply {
    private final boolean isReadOnly;

    private DelayedROStatusApply(VirtualFile f, boolean isReadOnly) {
      super(f);
      this.isReadOnly = isReadOnly;
    }

    @Override
    public void apply() throws IOException {
      ReadOnlyAttributeUtil.setReadOnlyAttribute(myFile, isReadOnly);
    }
  }

  public static final class RuntimeIOException extends RuntimeException {
    public RuntimeIOException(Throwable cause) {
      super(cause);
    }
  }
}
