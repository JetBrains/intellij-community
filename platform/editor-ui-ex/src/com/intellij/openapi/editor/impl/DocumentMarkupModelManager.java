// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

@Service
public final class DocumentMarkupModelManager {
  private static final Logger LOG = Logger.getInstance(DocumentMarkupModelManager.class);

  private final WeakList<Document> myDocumentSet = new WeakList<>();
  private volatile boolean myDisposed;

  public static DocumentMarkupModelManager getInstance(@NotNull Project project) {
    return project.getService(DocumentMarkupModelManager.class);
  }

  public DocumentMarkupModelManager(@NotNull Project project) {
    Disposer.register(project, () -> cleanupProjectMarkups(project));
  }

  public void registerDocument(Document document) {
    LOG.assertTrue(!myDisposed);
    myDocumentSet.add(document);
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  private void cleanupProjectMarkups(@NotNull Project project) {
    if (!myDisposed) {
      myDisposed = true;
      for (Document document : myDocumentSet.toStrongList()) {
        DocumentMarkupModel.removeMarkupModel(document, project);
      }
    }
  }
}
