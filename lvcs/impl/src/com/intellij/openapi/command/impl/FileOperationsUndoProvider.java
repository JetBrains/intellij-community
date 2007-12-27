package com.intellij.openapi.command.impl;

import com.intellij.ProjectTopics;
import com.intellij.history.Checkpoint;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileOperationsUndoProvider extends VirtualFileAdapter implements UndoProvider, Disposable {
  private Key<Boolean> DELETION_WAS_UNDOABLE = new Key<Boolean>("DeletionWasUndoable");

  private Project myProject;
  private boolean myIsInsideCommand;

  private List<MyUndoableAction> myCommandActions;
  private MessageBusConnection myBusConnection;

  public FileOperationsUndoProvider() {
    this(null, null);
  }

  public FileOperationsUndoProvider(Project p, MessageBus bus) {
    myProject = p;
    if (myProject == null) return;

    myBusConnection = bus.connect();

    getFileManager().addVirtualFileListener(this);
    listenForModuleChanges();
  }

  private void listenForModuleChanges() {
    // We have to invalidate all complex commands, that affect file system, in order to
    // prevent deletion and recreation changed roots during undo.
    //
    // Also, undoing roots creation/deletion causes dead-lock in local history, because
    // roots changes notifications are sent in separate thread, while local history is locked
    // inside revert method.
    //
    // Another point is that local history does not distinguish creation of file from addition of
    // content root, thereby, if already existed content root was just added to module, it will
    // be physically removed from dist during revert.

    myBusConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(final ModuleRootEvent event) {
      }

      public void rootsChanged(final ModuleRootEvent event) {
        ((UndoManagerImpl)UndoManager.getInstance(myProject)).invalidateAllComplexCommands();
      }
    });
  }

  public void dispose() {
    if (myProject == null) return;

    myBusConnection.disconnect();
    getFileManager().removeVirtualFileListener(this);
  }

  private VirtualFileManager getFileManager() {
    return VirtualFileManager.getInstance();
  }

  public void commandStarted(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = true;
    myCommandActions = new ArrayList<MyUndoableAction>();
  }

  public void commandFinished(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = false;
    if (myCommandActions.isEmpty()) return;
    myCommandActions.get(0).beFirstInCommand();
    myCommandActions.get(myCommandActions.size() - 1).beLastInCommand();
  }

  public void fileCreated(VirtualFileEvent e) {
    processEvent(e);
  }

  public void propertyChanged(VirtualFilePropertyEvent e) {
    if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;
    processEvent(e);
  }

  public void fileMoved(VirtualFileMoveEvent e) {
    processEvent(e);
  }

  private void processEvent(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;
    if (isUndoable(e)) {
      createUndoableAction(e, false);
    }
    else {
      createNonUndoableAction(e);
    }
  }

  public void beforeContentsChange(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;
    if (isUndoable(e)) return;
    createNonUndoableAction(e);
  }

  public void beforeFileDeletion(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;
    if (nonUndoableDeletion(e)) return;
    if (isUndoable(e)) {
      e.getFile().putUserData(DELETION_WAS_UNDOABLE, true);
    }
    else {
      createNonUndoableDeletionAction(e);
    }
  }

  private boolean nonUndoableDeletion(VirtualFileEvent e) {
    return LocalHistory.hasUnavailableContent(myProject, e.getFile());
  }

  public void fileDeleted(VirtualFileEvent e) {
    VirtualFile f = e.getFile();

    if (f.getUserData(DELETION_WAS_UNDOABLE) != null) {
      createUndoableAction(e, true);
      f.putUserData(DELETION_WAS_UNDOABLE, null);
    }
  }

  private boolean shouldNotProcess(VirtualFileEvent e) {
    return isProjectClosed() || !LocalHistory.isUnderControl(myProject, e.getFile());
  }

  private boolean isProjectClosed() {
    return myProject.isDisposed();
  }

  private boolean isUndoable(VirtualFileEvent e) {
    return !e.isFromRefresh();
  }

  private void createNonUndoableAction(VirtualFileEvent e) {
    createNonUndoableAction(e, false);
  }

  private void createNonUndoableDeletionAction(VirtualFileEvent e) {
    createNonUndoableAction(e, true);
  }

  private void createNonUndoableAction(VirtualFileEvent e, boolean isDeletion) {
    VirtualFile f = e.getFile();
    DocumentReference r = createDocumentReference(f, isDeletion);
    registerNonUndoableAction(r);

    DocumentReference oldRef = getUndoManager().findInvalidatedReferenceByUrl(f.getUrl());
    if (oldRef != null && !oldRef.equals(r)) {
      registerNonUndoableAction(oldRef);
    }
  }

  private void registerNonUndoableAction(final DocumentReference r) {
    if (!getUndoManager().documentWasChanged(r)) return;

    getUndoManager().undoableActionPerformed(new NonUndoableAction() {
      public DocumentReference[] getAffectedDocuments() {
        return new DocumentReference[]{r};
      }

      public boolean isComplex() {
        return true;
      }
    });
  }

  private void createUndoableAction(VirtualFileEvent e, boolean isDeletion) {
    if (!myIsInsideCommand) return;

    DocumentReference ref = createDocumentReference(e.getFile(), isDeletion);
    MyUndoableAction a = new MyUndoableAction(ref);

    getUndoManager().undoableActionPerformed(a);
    myCommandActions.add(a);
  }

  private DocumentReference createDocumentReference(VirtualFile f, boolean isDeletion) {
    DocumentReference r = new DocumentReferenceByVirtualFile(f);
    if (isDeletion) r.beforeFileDeletion(f);
    return r;
  }

  private UndoManagerImpl getUndoManager() {
    if (myProject != null) {
      return (UndoManagerImpl) UndoManager.getInstance(myProject);
    }
    return (UndoManagerImpl) UndoManager.getGlobalInstance();
  }

  private class MyUndoableAction implements UndoableAction {
    private DocumentReference myDocumentRef;
    private Checkpoint myAfterActionCheckpoint;
    private Checkpoint myBeforeUndoCheckpoint;
    private boolean myProcessDuringUndo;
    private boolean myProcessDuringRedo;

    public MyUndoableAction(DocumentReference r) {
      myDocumentRef = r;
      myAfterActionCheckpoint = LocalHistory.putCheckpoint(myProject);
    }

    public void beFirstInCommand() {
      myProcessDuringUndo = true;
    }

    public void beLastInCommand() {
      myProcessDuringRedo = true;
    }

    public void undo() throws UnexpectedUndoException {
      myBeforeUndoCheckpoint = LocalHistory.putCheckpoint(myProject);

      if (!myProcessDuringUndo) return;
      try {
        myAfterActionCheckpoint.revertToPreviousState();
      }
      catch (IOException e) {
        throw new UnexpectedUndoException(e.getMessage());
      }
    }

    public void redo() throws UnexpectedUndoException {
      if (!myProcessDuringRedo) return;
      try {
        myBeforeUndoCheckpoint.revertToThatState();
      }
      catch (IOException e) {
        throw new UnexpectedUndoException(e.getMessage());
      }
    }

    public DocumentReference[] getAffectedDocuments() {
      return new DocumentReference[]{myDocumentRef};
    }

    public boolean isComplex() {
      return true;
    }
  }
}
