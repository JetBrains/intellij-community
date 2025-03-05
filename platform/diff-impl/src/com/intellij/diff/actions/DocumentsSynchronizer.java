// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;

@ApiStatus.Internal
public abstract class DocumentsSynchronizer {
  protected final @NotNull Document myDocument1;
  protected final @NotNull Document myDocument2;
  protected final @Nullable Project myProject;

  protected boolean myDuringModification = false;

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

  public @NotNull Document getDocument1() {
    return myDocument1;
  }

  public @NotNull Document getDocument2() {
    return myDocument2;
  }

  protected abstract void onDocumentChanged1(@NotNull DocumentEvent event);

  protected abstract void onDocumentChanged2(@NotNull DocumentEvent event);

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

  public static @NotNull Document createFakeDocument(@NotNull Document original) {
    EditorFactoryImpl editorFactory = (EditorFactoryImpl)EditorFactory.getInstance();
    boolean acceptsSlashR = original instanceof DocumentImpl && ((DocumentImpl)original).acceptsSlashR();
    boolean writeThreadOnly = original instanceof DocumentImpl && ((DocumentImpl)original).isWriteThreadOnly();
    Document document = editorFactory.createDocument("", acceptsSlashR, !writeThreadOnly);
    document.putUserData(UndoManager.ORIGINAL_DOCUMENT, original);
    return document;
  }
}
