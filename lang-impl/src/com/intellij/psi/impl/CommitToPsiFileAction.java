package com.intellij.psi.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentRunnable;


public abstract class CommitToPsiFileAction extends DocumentRunnable {
  protected CommitToPsiFileAction(Document document) {
    super(document);
  }
}




