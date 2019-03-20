// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;

public interface ChangesManager {


  String makePageTextUpToDate(String initialPageText, long pageNumber);

  Change tryRegisterUndoAndGetCorrespondingChange();

  Change tryRegisterRedoAndGetCorrespondingChange();

  void setEnabledListeningDocumentChanges(@NotNull Document document, boolean enabled);

  void onDocumentChangedEvent(DocumentEvent event);

  void clear();
}
