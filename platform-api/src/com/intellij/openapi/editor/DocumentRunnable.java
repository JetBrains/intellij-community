package com.intellij.openapi.editor;

/**
 * @author cdr
 */
public abstract class DocumentRunnable implements Runnable {
  protected final Document myDocument;

  public DocumentRunnable(Document document) {
    myDocument = document;
  }

  public Document getDocument() {
    return myDocument;
  }
}
