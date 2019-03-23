// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

import com.intellij.largeFilesEditor.editor.EditorManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class ChangesManagerImpl implements ChangesManager {
  private static final Logger LOG = Logger.getInstance(ChangesManagerImpl.class);
  private static final Key<Boolean> REGISTER_DOCUMENT_CHANGES_ENABLED =
    Key.create("LFE.ChangesManagerImpl.REGISTER_DOCUMENT_CHANGES_ENABLED");

  private final ChangesStorage myChangesStorage;

  public ChangesManagerImpl() {
    myChangesStorage = new ChangesStorageImpl();
  }

  @Override
  public String makePageTextUpToDate(String initialPageText, long pageNumber) {
    PageValidChangesList<LocalChange> localChangesForPage = myChangesStorage.getLocalChangesForPage(pageNumber);
    if (localChangesForPage != null) {
      StringBuilder stringBuilder = new StringBuilder(initialPageText);
      localChangesForPage.forEach(localChange -> localChange.performRedo(stringBuilder));
      return stringBuilder.toString();
    }
    return initialPageText;
  }

  @Override
  public Change tryRegisterUndoAndGetCorrespondingChange() {
    return myChangesStorage.tryRegisterUndoAndGetCorrespondingChange();
  }

  @Override
  public Change tryRegisterRedoAndGetCorrespondingChange() {
    return myChangesStorage.tryRegisterRedoAndGetCorrespondingChange();
  }

  @Override
  public void setEnabledListeningDocumentChanges(@NotNull Document document, boolean enabled) {
    document.putUserData(REGISTER_DOCUMENT_CHANGES_ENABLED, enabled ? Boolean.TRUE : null);
  }

  @Override
  public void onDocumentChangedEvent(DocumentEvent event) {
    if (event.getDocument().getUserData(REGISTER_DOCUMENT_CHANGES_ENABLED) == Boolean.TRUE) {
      Object pageNumberObject = event.getDocument().getUserData(EditorManager.KEY_DOCUMENT_PAGE_NUMBER);
      long pageNumber = (long)pageNumberObject;
      LocalChange localChange = new LocalChange(pageNumber, event);
      myChangesStorage.addNewLocalChange(localChange);

      if (LOG.isDebugEnabled()) {
        LOG.debug("added new local change: " + localChange.toString());
      }
    }
  }

  @Override
  public void clear() {
    myChangesStorage.clear();
  }
}
