// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.base;

import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public abstract class ListenerDiffViewerBase extends DiffViewerBase {
  public ListenerDiffViewerBase(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);
  }

  @Override
  protected void onInit() {
    super.onInit();

    List<VirtualFile> files = extractVirtualFiles(myRequest);
    if (!files.isEmpty()) {
      ApplicationManager.getApplication().getMessageBus().connect(this)
        .subscribe(VirtualFileManager.VFS_CHANGES, new MyBulkFileListener(files));
    }

    List<Document> documents = ContainerUtil.mapNotNull(myRequest.getContents(), (content) -> {
      return content instanceof DocumentContent ? ((DocumentContent)content).getDocument() : null;
    });
    TextDiffViewerUtil.installDocumentListeners(new MyDocumentListener(), documents, this);
  }

  @NotNull
  private static List<VirtualFile> extractVirtualFiles(@NotNull ContentDiffRequest request) {
    List<VirtualFile> files = new ArrayList<>(0);
    for (DiffContent content : request.getContents()) {
      if (content instanceof DocumentContent) continue; // handled by DocumentListener
      if (content instanceof FileContent) {
        files.add(((FileContent)content).getFile());
      }
    }
    return files;
  }

  //
  // Abstract
  //

  @RequiresEdt
  protected void onDocumentChange(@NotNull DocumentEvent event) {
    scheduleRediff();
  }

  @RequiresEdt
  protected void onBeforeDocumentChange(@NotNull DocumentEvent event) {
  }

  @RequiresEdt
  protected void onFileChange(@NotNull VirtualFile file) {
    scheduleRediff();
  }


  private class MyDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
      onBeforeDocumentChange(event);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      onDocumentChange(event);
    }
  }

  private class MyBulkFileListener implements BulkFileListener {
    private final List<VirtualFile> myFiles;

    MyBulkFileListener(@NotNull List<VirtualFile> files) {
      myFiles = files;
    }

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
      Set<VirtualFile> toRefresh = new HashSet<>();
      for (VFileEvent event : events) {
        if (event instanceof VFileContentChangeEvent ||
            event instanceof VFilePropertyChangeEvent) {
          VirtualFile file = Objects.requireNonNull(event.getFile());
          if (myFiles.contains(file)) {
            toRefresh.add(file);
          }
        }
      }

      for (VirtualFile file : toRefresh) {
        onFileChange(file);
      }
    }
  }
}
