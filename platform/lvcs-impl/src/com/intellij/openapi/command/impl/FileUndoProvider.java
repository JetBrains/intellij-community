package com.intellij.openapi.command.impl;

import com.intellij.ProjectTopics;
import com.intellij.history.LocalHistory;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.changes.Change;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryComponent;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import java.io.IOException;

public class FileUndoProvider extends VirtualFileAdapter implements UndoProvider {
  public static final Logger LOG = Logger.getInstance("#" + FileUndoProvider.class.getName());

  private final Key<DocumentReference> DELETION_WAS_UNDOABLE = new Key<DocumentReference>("DeletionWasUndoable");

  private final Project myProject;
  private boolean myIsInsideCommand;

  private LocalVcs myLocalHistory;
  private IdeaGateway myGateway;

  private Change myLastChange;

  @SuppressWarnings({"UnusedDeclaration"})
  public FileUndoProvider() {
    this(null, null);
  }

  public FileUndoProvider(Project project, MessageBus bus) {
    myProject = project;
    if (myProject == null) return;

    myLocalHistory = LocalHistoryComponent.getLocalVcsFor(myProject);
    myGateway = LocalHistoryComponent.getGatewayFor(myProject);

    getFileManager().addVirtualFileListener(this, project);
    listenForModuleChanges(bus.connect(project));
    listenForLocalHistory();
  }

  private void listenForLocalHistory() {
    myLocalHistory.addListener(new LocalVcs.Listener() {
      public void onChange(Change c) {
        myLastChange = c;
      }
    });
  }

  private void listenForModuleChanges(MessageBusConnection bus) {
    // We have to invalidate all complex commands, that affect file system, in order to
    // prevent deletion and recreation changed roots during undo.
    //
    // The point is that local history does not distinguish creation of file from addition of
    // content root, thereby, if already existed content root was just added to module, it will
    // be physically removed from dist during revert.

    bus.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(final ModuleRootEvent event) {
      }

      public void rootsChanged(final ModuleRootEvent event) {
        ((UndoManagerImpl)UndoManager.getInstance(myProject)).invalidateAllGlobalActions();
      }
    });
  }

  private static VirtualFileManager getFileManager() {
    return VirtualFileManager.getInstance();
  }

  public void commandStarted(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = true;
  }

  public void commandFinished(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = false;
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
      registerUndoableAction(e);
    }
    else {
      registerNonUndoableAction(e);
    }
  }

  public void beforeContentsChange(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;
    if (isUndoable(e)) return;
    registerNonUndoableAction(e);
  }

  public void beforeFileDeletion(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;
    if (isUndoable(e)) {
      // only check if the action is undoable to prevent unnecessary
      // content reading during refresh.
      if (nonUndoableDeletion(e)) return;
      VirtualFile file = e.getFile();
      file.putUserData(DELETION_WAS_UNDOABLE, createDocumentReference(e));
      LocalHistoryComponent.getComponentInstance(myProject).registerUnsavedDocuments(file);
    }
    else {
      registerNonUndoableAction(e);
    }
  }

  private boolean nonUndoableDeletion(VirtualFileEvent e) {
    return LocalHistory.hasUnavailableContent(myProject, e.getFile());
  }

  public void fileDeleted(VirtualFileEvent e) {
    VirtualFile f = e.getFile();

    DocumentReference ref = f.getUserData(DELETION_WAS_UNDOABLE);
    if (ref != null) {
      registerUndoableAction(ref);
      f.putUserData(DELETION_WAS_UNDOABLE, null);
    }
  }

  private boolean shouldNotProcess(VirtualFileEvent e) {
    return isProjectClosed() || !LocalHistory.isUnderControl(myProject, e.getFile()) || !myIsInsideCommand;
  }

  private boolean isProjectClosed() {
    return myProject.isDisposed();
  }

  private boolean isUndoable(VirtualFileEvent e) {
    return !e.isFromRefresh();
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

  private DocumentReference createDocumentReference(VirtualFileEvent e) {
    return DocumentReferenceManager.getInstance().create(e.getFile());
  }

  private UndoManagerImpl getUndoManager() {
    if (myProject != null) {
      return (UndoManagerImpl)UndoManager.getInstance(myProject);
    }
    return (UndoManagerImpl)UndoManager.getGlobalInstance();
  }

  private class MyUndoableAction implements UndoableAction {
    private final DocumentReference[] myReferences;
    private ChangeRange myActionChangeRange;
    private ChangeRange myUndoChangeRange;

    public MyUndoableAction(DocumentReference r) {
      myReferences = new DocumentReference[]{r};
      myActionChangeRange = new ChangeRange(myGateway, myLocalHistory, myLastChange);
    }

    public void undo() throws UnexpectedUndoException {
      try {
        myUndoChangeRange = myActionChangeRange.revert(myUndoChangeRange);
      }
      catch (IOException e) {
        LOG.warn(e);
        throw new UnexpectedUndoException(e.getMessage());
      }
    }

    public void redo() throws UnexpectedUndoException {
      try {
        myActionChangeRange = myUndoChangeRange.revert(myActionChangeRange);
      }
      catch (IOException e) {
        LOG.warn(e);
        throw new UnexpectedUndoException(e.getMessage());
      }
    }

    public DocumentReference[] getAffectedDocuments() {
      return myReferences;
    }

    public boolean isGlobal() {
      return true;
    }
  }
}
