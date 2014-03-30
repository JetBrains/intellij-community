/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class DocumentMarkupModelManager extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.DocumentMarkupModelManager");

  private final WeakList<DocumentImpl> myDocumentSet = new WeakList<DocumentImpl>();
  private volatile boolean myDisposed;

  public static DocumentMarkupModelManager getInstance(Project project) {
    return project.getComponent(DocumentMarkupModelManager.class);
  }

  public DocumentMarkupModelManager(@NotNull Project project) {
    super(project);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        cleanupProjectMarkups();
      }
    });
  }

  public void registerDocument(DocumentImpl document) {
    LOG.assertTrue(!myDisposed);
    myDocumentSet.add(document);
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  private void cleanupProjectMarkups() {
    if (!myDisposed) {
      myDisposed = true;
      for (DocumentImpl document : myDocumentSet.toStrongList()) {
        DocumentMarkupModel.removeMarkupModel(document, myProject);
      }
    }
  }
}
