/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.util.Function;

import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author ven
 */
public class ReferenceEditorWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor{
  private final Function<String,Document> myFactory;
  private List<DocumentListener> myDocumentListeners = new CopyOnWriteArrayList<DocumentListener>();

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener, final Project project, final Function<String,Document> factory, String text) {
    this(browseActionListener, new EditorTextField(factory.fun(text), project, StdFileTypes.JAVA), factory);
  }

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener, final EditorTextField editorTextField, final Function<String,Document> factory) {
    super(editorTextField, browseActionListener);
    myFactory = factory;
  }

  public void addDocumentListener(DocumentListener listener) {
    myDocumentListeners.add(listener);
    getEditorTextField().getDocument().addDocumentListener(listener);
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentListeners.remove(listener);
    getEditorTextField().getDocument().removeDocumentListener(listener);
  }

  public EditorTextField getEditorTextField() {
    return getChildComponent();
  }

  public String getText(){
    return getEditorTextField().getText().trim();
  }

  public void setText(final String text) {
    Document oldDocument = getEditorTextField().getDocument();
    String oldText = oldDocument.getText();
    for(DocumentListener listener: myDocumentListeners) {
      oldDocument.removeDocumentListener(listener);
    }
    Document document = myFactory.fun(text);
    getEditorTextField().setDocument(document);
    for(DocumentListener listener: myDocumentListeners) {
      document.addDocumentListener(listener);
      listener.documentChanged(new DocumentEventImpl(document, 0, oldText, text, -1));
    }
  }

  public boolean isEditable() {
    return !getEditorTextField().getEditor().isViewer();
  }
}
