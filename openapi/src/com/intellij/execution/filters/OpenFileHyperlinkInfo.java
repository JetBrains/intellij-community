/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.filters;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public final class OpenFileHyperlinkInfo implements HyperlinkInfo {
  private final OpenFileDescriptor myDescriptor;

  public OpenFileHyperlinkInfo(final OpenFileDescriptor descriptor) {
    myDescriptor = descriptor;
  }

  public OpenFileHyperlinkInfo(Project project, final VirtualFile file, final int line, final int column) {
    this (new OpenFileDescriptor(project, file, line, column));
  }

  public OpenFileHyperlinkInfo(Project project, final VirtualFile file, final int line) {
    this (project, file, line, 0);
  }

  public OpenFileDescriptor getDescriptor() {
    return myDescriptor;
  }

  public void navigate(final Project project) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final VirtualFile file = myDescriptor.getFile();
        if(file != null && file.isValid()) {
          FileEditorManager.getInstance(project).openTextEditor(myDescriptor, true);
        }
      }
    });
  }
}
