package com.intellij.psi.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.project.Project;


public abstract class CommitToPsiFileAction extends DocumentRunnable {
  protected CommitToPsiFileAction(Document document, Project project) {
    super(document,project);
  }
}




