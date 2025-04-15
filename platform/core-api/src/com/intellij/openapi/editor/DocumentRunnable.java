// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated unused
 */
@Deprecated
public abstract class DocumentRunnable implements Runnable {
  private final Document myDocument;
  private final Project myProject;

  public DocumentRunnable(@Nullable Document document, Project project) {
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
