// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.base;

import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class ListenerDiffViewerBase extends DiffViewerBase {
  public ListenerDiffViewerBase(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);
  }

  @Override
  protected void onInit() {
    super.onInit();

    VirtualFileListener fileListener = createFileListener(myRequest);
    if (fileListener != null) VirtualFileManager.getInstance().addVirtualFileListener(fileListener, this);

    DocumentListener documentListener = createDocumentListener();
    List<Document> documents = ContainerUtil.mapNotNull(myRequest.getContents(), (content) -> content instanceof DocumentContent ? ((DocumentContent)content).getDocument() : null);
    TextDiffViewerUtil.installDocumentListeners(documentListener, documents, this);
  }

  @NotNull
  protected DocumentListener createDocumentListener() {
    return new DocumentListener() {
      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent event) {
        onBeforeDocumentChange(event);
      }

      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        onDocumentChange(event);
      }
    };
  }

  @Nullable
  protected VirtualFileListener createFileListener(@NotNull ContentDiffRequest request) {
    final List<VirtualFile> files = new ArrayList<>(0);
    for (DiffContent content : request.getContents()) {
      if (content instanceof FileContent && !(content instanceof DocumentContent)) {
        files.add(((FileContent)content).getFile());
      }
    }

    if (files.isEmpty()) return null;

    return new VirtualFileListener() {
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
}
