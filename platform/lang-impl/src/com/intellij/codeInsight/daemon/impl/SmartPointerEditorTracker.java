/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

public class SmartPointerEditorTracker extends AbstractProjectComponent implements DocumentListener, EditorFactoryListener, DocumentBulkUpdateListener {
  private final SmartPointerManagerImpl mySmartPointerManager;

  public SmartPointerEditorTracker(Project project,
                                   EditorFactory editorFactory,
                                   SmartPointerManager manager,
                                   MessageBus bus) {
    super(project);
    mySmartPointerManager = (SmartPointerManagerImpl)manager;
    editorFactory.getEventMulticaster().addDocumentListener(this, project);
    editorFactory.addEditorFactoryListener(this, project);
    bus.connect().subscribe(DocumentBulkUpdateListener.TOPIC, this);
  }


  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    Document document = event.getDocument();
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    boolean isBulk = document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate();

    boolean isRelevant = virtualFile != null && isRelevant(virtualFile);
    if (!isBulk && isRelevant && shouldNotifySmartPointers(virtualFile)) {
      mySmartPointerManager.fastenBelts(virtualFile, event.getOffset(), null);
    }
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    final Document document = event.getDocument();
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    boolean isBulk = document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate();

    boolean isRelevant = virtualFile != null && isRelevant(virtualFile);
    if (!isBulk && isRelevant && shouldNotifySmartPointers(virtualFile)) {
      mySmartPointerManager.unfastenBelts(virtualFile, event.getOffset());
    }
  }

  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
    final Editor editor = event.getEditor();
    if (editor.getProject() != null && editor.getProject() != myProject || myProject.isDisposed()) return;
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    if (psiFile == null) return;

    final VirtualFile virtualFile = psiFile.getVirtualFile();
    // materialize all range markers and do not let them to be collected to improve responsiveness
    if (virtualFile != null) {
      mySmartPointerManager.fastenBelts(virtualFile, 0, null);
    }
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    final Editor editor = event.getEditor();
    if (editor.getProject() != null && editor.getProject() != myProject || myProject.isDisposed()) return;
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    if (psiFile == null) return;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    // allow range markers in smart pointers to be collected
    // beware there maybe other editors still open for that file
    if (virtualFile != null && virtualFile.isValid() && shouldNotifySmartPointers(virtualFile)) {
      mySmartPointerManager.unfastenBelts(virtualFile, 0);
    }
  }

  private boolean shouldNotifySmartPointers(@NotNull VirtualFile virtualFile) {
    // for an open file do not do fasten/unfasten, they should always stay fastened to improve responsiveness
    return !FileEditorManager.getInstance(myProject).isFileOpen(virtualFile);
  }
  private boolean isRelevant(@NotNull VirtualFile virtualFile) {
    return !virtualFile.getFileType().isBinary() && !myProject.isDisposed();
  }

  @Override
  public void updateStarted(@NotNull Document document) {
    final VirtualFile virtualFile = getVirtualFile(document);
    if (virtualFile != null && isRelevant(virtualFile) && shouldNotifySmartPointers(virtualFile)) {
      mySmartPointerManager.fastenBelts(virtualFile, 0, null);
    }
  }

  @Override
  public void updateFinished(@NotNull Document document) {
    final VirtualFile virtualFile = getVirtualFile(document);
    if (virtualFile != null && isRelevant(virtualFile) && shouldNotifySmartPointers(virtualFile)) {
      mySmartPointerManager.unfastenBelts(virtualFile, 0);
    }
  }

  private static VirtualFile getVirtualFile(@NotNull Document document) {
    final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    return virtualFile == null || !virtualFile.isValid() ? null : virtualFile;
  }
}
