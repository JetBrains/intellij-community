// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;

public class MockChangesManager implements ChangesManager {
  @Override
  public String makePageTextUpToDate(String initialPageText, long pageNumber) {
    return initialPageText;
  }

  @Override
  public Change tryRegisterUndoAndGetCorrespondingChange() {
    return null;
  }

  @Override
  public Change tryRegisterRedoAndGetCorrespondingChange() {
    return null;
  }

  @Override
  public void setEnabledListeningDocumentChanges(@NotNull Document document, boolean enabled) {
  }

  @Override
  public void onDocumentChangedEvent(DocumentEvent event) {
  }

  @Override
  public void clear() {
  }
}
