package com.intellij.openapi.util.diff.tools.util.base;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.contents.BinaryFileContent;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.contents.DocumentContent;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class ListenerDiffViewerBase extends DiffViewerBase {
  @NotNull private final DocumentListener myDocumentListener;
  @Nullable private final VirtualFileListener myFileListener;

  public ListenerDiffViewerBase(@NotNull FrameDiffTool.DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);
    myDocumentListener = createDocumentListener();
    myFileListener = createFileListener(request);

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
        scheduleRediff();
      }
    };
  }

  @Nullable
  protected VirtualFileListener createFileListener(@NotNull ContentDiffRequest request) {
    final List<VirtualFile> files = new ArrayList<VirtualFile>(0);
    for (DiffContent content : request.getContents()) {
      if (content instanceof BinaryFileContent) {
        files.add(((BinaryFileContent)content).getFile());
      }
    }

    if (files.isEmpty()) return null;

    return new VirtualFileAdapter() {
      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        if (files.contains(event.getFile())) {
          onFileChange(event);
          scheduleRediff();
        }
      }

      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (files.contains(event.getFile())) {
          onFileChange(event);
          scheduleRediff();
        }
      }
    };
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void onDocumentChange(@NotNull DocumentEvent event) {
  }

  @CalledInAwt
  protected void onBeforeDocumentChange(@NotNull DocumentEvent event) {
  }

  @CalledInAwt
  protected void onFileChange(@NotNull VirtualFileEvent event) {
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
