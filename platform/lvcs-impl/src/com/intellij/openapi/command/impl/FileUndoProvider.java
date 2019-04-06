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
import com.intellij.openapi.vfs.*;
import com.intellij.util.FileContentUtilCore;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class FileUndoProvider implements UndoProvider, VirtualFileListener {
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

    LocalHistoryImpl localHistory = LocalHistoryImpl.getInstanceImpl();
    myLocalHistory = localHistory.getFacade();
    myGateway = localHistory.getGateway();
    if (myLocalHistory == null || myGateway == null) return; // local history was not initialized (e.g. in headless environment)

    localHistory.addVFSListenerAfterLocalHistoryOne(this, project);
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
  public void fileCreated(@NotNull VirtualFileEvent e) {
    processEvent(e);
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent e) {
    if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;
    processEvent(e);
  }

  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent e) {
    processEvent(e);
  }

  private void processEvent(VirtualFileEvent e) {
    if (!shouldProcess(e)) return;
    if (isUndoable(e)) {
      registerUndoableAction(e);
    }
    else {
      registerNonUndoableAction(e);
    }
  }

  @Override
  public void beforeContentsChange(@NotNull VirtualFileEvent e) {
    if (!shouldProcess(e)) return;
    if (isUndoable(e)) return;
    registerNonUndoableAction(e);
  }

  @Override
  public void beforeFileDeletion(@NotNull VirtualFileEvent e) {
    if (!shouldProcess(e)) {
      invalidateActionsFor(e);
      return;
    }
    if (isUndoable(e)) {
      VirtualFile file = e.getFile();
      file.putUserData(DELETION_WAS_UNDOABLE, createDocumentReference(e));
    }
    else {
      registerNonUndoableAction(e);
    }
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent e) {
    if (!shouldProcess(e)) return;
    VirtualFile f = e.getFile();

    DocumentReference ref = f.getUserData(DELETION_WAS_UNDOABLE);
    if (ref != null) {
      registerUndoableAction(ref);
      f.putUserData(DELETION_WAS_UNDOABLE, null);
    }
  }

  private boolean shouldProcess(@NotNull VirtualFileEvent e) {
    if (!myIsInsideCommand || myProject.isDisposed()) {
      return false;
    }

    Object requestor = e.getRequestor();
    if (FileContentUtilCore.FORCE_RELOAD_REQUESTOR.equals(requestor) || requestor instanceof StorageManagerFileWriteRequestor) {
      return false;
    }
    return LocalHistory.getInstance().isUnderControl(e.getFile());
  }

  private static boolean isUndoable(VirtualFileEvent e) {
    return !e.isFromRefresh() || e.getFile().getUserData(UndoConstants.FORCE_RECORD_UNDO) == Boolean.TRUE;
  }

  private void registerUndoableAction(VirtualFileEvent e) {
    registerUndoableAction(createDocumentReference(e));
  }

  private void registerUndoableAction(DocumentReference ref) {
    getUndoManager().undoableActionPerformed(new MyUndoableAction(ref));
  }

  private void registerNonUndoableAction(VirtualFileEvent e) {
    getUndoManager().nonundoableActionPerformed(createDocumentReference(e), true);
  }

  private void invalidateActionsFor(VirtualFileEvent e) {
    if (myProject == null || !myProject.isDisposed()) {
      getUndoManager().invalidateActionsFor(createDocumentReference(e));
    }
  }

  private static DocumentReference createDocumentReference(VirtualFileEvent e) {
    return DocumentReferenceManager.getInstance().create(e.getFile());
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
