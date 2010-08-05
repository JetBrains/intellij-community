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

package com.intellij.openapi.command.impl;

import com.intellij.history.LocalHistory;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ContentChange;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;

import java.io.IOException;

public class FileUndoProvider extends VirtualFileAdapter implements UndoProvider {
  public static final Logger LOG = Logger.getInstance("#" + FileUndoProvider.class.getName());

  private final Key<DocumentReference> DELETION_WAS_UNDOABLE = new Key<DocumentReference>("DeletionWasUndoable");

  private final Project myProject;
  private boolean myIsInsideCommand;

  private LocalHistoryFacade myLocalHistory;
  private IdeaGateway myGateway;

  private Change myLastChange;

  @SuppressWarnings({"UnusedDeclaration"})
  public FileUndoProvider() {
    this(null);
  }

  public FileUndoProvider(Project project) {
     myProject = project;
    if (myProject == null) return;

    myLocalHistory = LocalHistoryImpl.getInstanceImpl().getFacade();
    myGateway = LocalHistoryImpl.getInstanceImpl().getGateway();
    if (myLocalHistory == null || myGateway == null) return; // local history was not initialized (e.g. in headless environment)

    getFileManager().addVirtualFileListener(this, project);
    myLocalHistory.addListener(new LocalHistoryFacade.Listener() {
      public void changeAdded(Change c) {
        if (!(c instanceof StructuralChange) || c instanceof ContentChange) return;
        myLastChange = c;
      }
    }, myProject);
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
      VirtualFile file = e.getFile();
      file.putUserData(DELETION_WAS_UNDOABLE, createDocumentReference(e));
    }
    else {
      registerNonUndoableAction(e);
    }
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
    return isProjectClosed() || !LocalHistory.getInstance().isUnderControl(e.getFile()) || !myIsInsideCommand;
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
