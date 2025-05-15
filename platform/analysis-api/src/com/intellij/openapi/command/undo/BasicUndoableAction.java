// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.command.undo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;

public abstract class BasicUndoableAction implements UndoableAction {
  private final DocumentReference[] myRefs;
  private long myPerformedTimestamp = -1L;

  public BasicUndoableAction() {
    myRefs = null;
  }

  public BasicUndoableAction(DocumentReference @Nullable ... refs) {
    myRefs = refs;
  }

  public BasicUndoableAction(Document @NotNull ... docs) {
    myRefs = new DocumentReference[docs.length];
    for (int i = 0; i < docs.length; i++) {
      myRefs[i] = DocumentReferenceManager.getInstance().create(docs[i]);
    }
  }

  public BasicUndoableAction(VirtualFile @NotNull ... files) {
    myRefs = new DocumentReference[files.length];
    for (int i = 0; i < files.length; i++) {
      myRefs[i] = DocumentReferenceManager.getInstance().create(files[i]);
    }
  }

  @Override
  public DocumentReference[] getAffectedDocuments() {
    return myRefs;
  }

  @Override
  public boolean isGlobal() {
    return false;
  }

  @Override
  public long getPerformedNanoTime() {
    return myPerformedTimestamp;
  }

  @Override
  public void setPerformedNanoTime(long l) {
    myPerformedTimestamp = l;
  }

  @Override
  public String toString() {
    return "%s{refs=%s%s, performed=%sms}".formatted(
      getClass().getName().replace(getClass().getPackageName() + ".", ""),
      Arrays.toString(getAffectedDocuments()),
      isGlobal() ? ", global" : "",
      getPerformedNanoTime() / 1000
    );
  }
}
