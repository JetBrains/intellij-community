// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;

abstract class DocumentsSynchronizer {
  @NotNull protected final Document myDocument1;
  @NotNull protected final Document myDocument2;
  @Nullable private final Project myProject;

  private boolean myDuringModification = false;

  private final DocumentListener myListener1 = new DocumentListener() {
    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      if (myDuringModification) return;
      onDocumentChanged1(e);
    }
  };

  private final DocumentListener myListener2 = new DocumentListener() {
    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      if (myDuringModification) return;
      onDocumentChanged2(e);
    }
  };

  private final PropertyChangeListener myROListener = event -> {
    if (Document.PROP_WRITABLE.equals(event.getPropertyName())) getDocument2().setReadOnly(!getDocument1().isWritable());
  };

  protected DocumentsSynchronizer(@Nullable Project project, @NotNull Document document1, @NotNull Document document2) {
    myProject = project;
    myDocument1 = document1;
    myDocument2 = document2;
  }

  @NotNull
  public Document getDocument1() {
    return myDocument1;
  }

  @NotNull
  public Document getDocument2() {
    return myDocument2;
  }

  protected abstract void onDocumentChanged1(@NotNull DocumentEvent event);

  protected abstract void onDocumentChanged2(@NotNull DocumentEvent event);

  @RequiresEdt
  protected void replaceString(@NotNull final Document document,
                               final int startOffset,
                               final int endOffset,
                               @NotNull final CharSequence newText) {
    try {
      myDuringModification = true;
      CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> document.replaceString(startOffset, endOffset, newText)),
                                                    DiffBundle.message("synchronize.document.and.its.fragment"), document);
    }
    finally {
      myDuringModification = false;
    }
  }

  public void startListen() {
    myDocument1.addDocumentListener(myListener1);
    myDocument2.addDocumentListener(myListener2);
    myDocument1.addPropertyChangeListener(myROListener);
  }

  public void stopListen() {
    myDocument1.removeDocumentListener(myListener1);
    myDocument2.removeDocumentListener(myListener2);
    myDocument1.removePropertyChangeListener(myROListener);
  }
}
