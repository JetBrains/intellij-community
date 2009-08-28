package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public abstract class DocumentRunnable implements Runnable {
  private final Document myDocument;
  private final Project myProject;

  public DocumentRunnable(@NotNull Document document, Project project) {
    myDocument = document;
    myProject = project;
  }

  public Document getDocument() {
    return myDocument;
  }

  public Project getProject() {
    return myProject;
  }
}
