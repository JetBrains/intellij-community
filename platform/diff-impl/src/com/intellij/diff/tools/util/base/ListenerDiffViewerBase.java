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
package com.intellij.diff.tools.util.base;

import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class ListenerDiffViewerBase extends DiffViewerBase {
  @NotNull private final DocumentListener myDocumentListener;
  @Nullable private final VirtualFileListener myFileListener;

  public ListenerDiffViewerBase(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);
    myDocumentListener = createDocumentListener();
    myFileListener = createFileListener(request);
  }

  @Override
  protected void onInit() {
    super.onInit();
    if (myFileListener != null) VirtualFileManager.getInstance().addVirtualFileListener(myFileListener);

    for (Document document : getDocuments()) {
      document.addDocumentListener(myDocumentListener);
    }
  }

  @Override
  protected void onDispose() {
    if (myFileListener != null) VirtualFileManager.getInstance().removeVirtualFileListener(myFileListener);

    for (Document document : getDocuments()) {
      document.removeDocumentListener(myDocumentListener);
    }
    super.onDispose();
  }

  @NotNull
  protected DocumentListener createDocumentListener() {
    return new DocumentAdapter() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
        onBeforeDocumentChange(event);
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        onDocumentChange(event);
      }
    };
  }

  @Nullable
  protected VirtualFileListener createFileListener(@NotNull ContentDiffRequest request) {
    final List<VirtualFile> files = new ArrayList<VirtualFile>(0);
    for (DiffContent content : request.getContents()) {
      if (content instanceof FileContent && !(content instanceof DocumentContent)) {
        files.add(((FileContent)content).getFile());
      }
    }

    if (files.isEmpty()) return null;

    return new VirtualFileAdapter() {
      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        if (files.contains(event.getFile())) {
          onFileChange(event);
        }
      }

      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (files.contains(event.getFile())) {
          onFileChange(event);
        }
      }
    };
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void onDocumentChange(@NotNull DocumentEvent event) {
    scheduleRediff();
  }

  @CalledInAwt
  protected void onBeforeDocumentChange(@NotNull DocumentEvent event) {
  }

  @CalledInAwt
  protected void onFileChange(@NotNull VirtualFileEvent event) {
    scheduleRediff();
  }

  //
  // Helpers
  //

  @NotNull
  private Set<Document> getDocuments() {
    Set<Document> documents = new HashSet<Document>();
    for (DiffContent content : myRequest.getContents()) {
      if (content instanceof DocumentContent) {
        documents.add(((DocumentContent)content).getDocument());
      }
    }
    return documents;
  }
}
