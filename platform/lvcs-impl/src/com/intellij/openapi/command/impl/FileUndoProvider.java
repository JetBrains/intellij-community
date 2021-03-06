// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.configurationStore.StorageManagerFileWriteRequestor;
import com.intellij.history.LocalHistory;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ContentChange;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryImpl;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.FileContentUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public final class FileUndoProvider implements UndoProvider, BulkFileListener {
  public static final Logger LOG = Logger.getInstance(FileUndoProvider.class);

  private final Key<DocumentReference> DELETION_WAS_UNDOABLE = new Key<>(FileUndoProvider.class.getName() + ".DeletionWasUndoable");

  private final Project myProject;
  private boolean myIsInsideCommand;

  private LocalHistoryFacade myLocalHistory;
  private IdeaGateway myGateway;

  private long myLastChangeId;

  @SuppressWarnings("UnusedDeclaration")
  public FileUndoProvider() {
    this(null);
  }

  private FileUndoProvider(Project project) {
    myProject = project;
    if (myProject == null) return;

    @NotNull LocalHistory localHistory = LocalHistory.getInstance();
    if (!(localHistory instanceof LocalHistoryImpl)) return;
    myLocalHistory = ((LocalHistoryImpl)localHistory).getFacade();
    myGateway = ((LocalHistoryImpl)localHistory).getGateway();
    if (myLocalHistory == null || myGateway == null) return; // local history was not initialized (e.g. in headless environment)

    ((LocalHistoryImpl)localHistory).addVFSListenerAfterLocalHistoryOne(this, project);
    myLocalHistory.addListener(new LocalHistoryFacade.Listener() {
      @Override
      public void changeAdded(Change c) {
        if (!(c instanceof StructuralChange) || c instanceof ContentChange) return;
        myLastChangeId = c.getId();
      }
    }, myProject);
  }

  @Override
  public void commandStarted(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = true;
  }

  @Override
  public void commandFinished(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = false;
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent e : events) {
      if (e instanceof VFileContentChangeEvent) {
        beforeContentsChange((VFileContentChangeEvent)e);
      }
      else if (e instanceof VFileDeleteEvent) {
        beforeFileDeletion((VFileDeleteEvent)e);
      }
    }
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent e : events) {
      if (e instanceof VFileCreateEvent ||
          e instanceof VFileMoveEvent ||
          e instanceof VFilePropertyChangeEvent && ((VFilePropertyChangeEvent)e).isRename()) {
        processEvent(e, e.getFile());
      }
      else if (e instanceof VFileCopyEvent) {
        processEvent(e, ((VFileCopyEvent)e).findCreatedFile());
      }
      else if (e instanceof VFileDeleteEvent) {
        fileDeleted((VFileDeleteEvent)e);
      }
    }
  }

  private void processEvent(@NotNull VFileEvent e, @Nullable VirtualFile file) {
    if (file == null || !shouldProcess(e, file)) return;
    if (isUndoable(e, file)) {
      registerUndoableAction(file);
    }
    else {
      registerNonUndoableAction(file);
    }
  }

  private void beforeContentsChange(@NotNull VFileContentChangeEvent e) {
    VirtualFile file = e.getFile();
    if (!shouldProcess(e, file)) return;
    if (isUndoable(e, file)) return;
    registerNonUndoableAction(file);
  }

  private void beforeFileDeletion(@NotNull VFileDeleteEvent e) {
    VirtualFile file = e.getFile();
    if (!shouldProcess(e, file)) {
      invalidateActionsFor(file);
      return;
    }
    if (isUndoable(e, file)) {
      file.putUserData(DELETION_WAS_UNDOABLE, createDocumentReference(file));
    }
    else {
      registerNonUndoableAction(file);
    }
  }

  private void fileDeleted(@NotNull VFileDeleteEvent e) {
    VirtualFile f = e.getFile();
    if (!shouldProcess(e, f)) return;

    DocumentReference ref = f.getUserData(DELETION_WAS_UNDOABLE);
    if (ref != null) {
      registerUndoableAction(ref);
      f.putUserData(DELETION_WAS_UNDOABLE, null);
    }
  }

  private boolean shouldProcess(@NotNull VFileEvent e, VirtualFile file) {
    if (!myIsInsideCommand || myProject.isDisposed()) {
      return false;
    }

    Object requestor = e.getRequestor();
    if (FileContentUtilCore.FORCE_RELOAD_REQUESTOR.equals(requestor) || requestor instanceof StorageManagerFileWriteRequestor) {
      return false;
    }
    return LocalHistory.getInstance().isUnderControl(file);
  }

  private static boolean isUndoable(@NotNull VFileEvent e, @NotNull VirtualFile file) {
    return !e.isFromRefresh() || UndoUtil.isForceUndoFlagSet(file);
  }

  private void registerUndoableAction(@NotNull VirtualFile file) {
    registerUndoableAction(createDocumentReference(file));
  }

  private void registerUndoableAction(DocumentReference ref) {
    getUndoManager().undoableActionPerformed(new MyUndoableAction(ref));
  }

  private void registerNonUndoableAction(@NotNull VirtualFile file) {
    getUndoManager().nonundoableActionPerformed(createDocumentReference(file), true);
  }

  private void invalidateActionsFor(@NotNull VirtualFile file) {
    if (myProject == null || !myProject.isDisposed()) {
      getUndoManager().invalidateActionsFor(createDocumentReference(file));
    }
  }

  private static DocumentReference createDocumentReference(@NotNull VirtualFile file) {
    return DocumentReferenceManager.getInstance().create(file);
  }

  private UndoManagerImpl getUndoManager() {
    if (myProject != null) {
      return (UndoManagerImpl)UndoManager.getInstance(myProject);
    }
    return (UndoManagerImpl)UndoManager.getGlobalInstance();
  }

  private class MyUndoableAction extends GlobalUndoableAction {
    private ChangeRange myActionChangeRange;
    private ChangeRange myUndoChangeRange;

    MyUndoableAction(DocumentReference r) {
      super(r);
      myActionChangeRange = new ChangeRange(myGateway, myLocalHistory, myLastChangeId);
    }

    @Override
    public void undo() throws UnexpectedUndoException {
      try {
        myUndoChangeRange = myActionChangeRange.revert(myUndoChangeRange);
      }
      catch (IOException e) {
        LOG.warn(e);
        throw new UnexpectedUndoException(e.getMessage());
      }
    }

    @Override
    public void redo() throws UnexpectedUndoException {
      try {
        myActionChangeRange = myUndoChangeRange.revert(myActionChangeRange);
      }
      catch (IOException e) {
        LOG.warn(e);
        throw new UnexpectedUndoException(e.getMessage());
      }
    }
  }
}
