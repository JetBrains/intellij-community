// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LookupDocumentSavingVetoer extends FileDocumentSynchronizationVetoer {
  @Override
  public boolean maySaveDocument(@NotNull Document document, boolean isSaveExplicit) {
    if (ApplicationManager.getApplication().isDisposed() || isSaveExplicit) {
      return true;
    }

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (!project.isInitialized() || project.isDisposed()) {
        continue;
      }
      LookupEx lookup = LookupManager.getInstance(project).getActiveLookup();
      if (lookup != null) {
        if (lookup.getTopLevelEditor().getDocument() == document) {
          return false;
        }
      }
    }
    return true;
  }

}
