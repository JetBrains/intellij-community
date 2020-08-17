// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Deprecated
public interface MergeVersion {
  Document createWorkingDocument(Project project);

  void applyText(@NotNull String text, Project project);

  @Nullable
  VirtualFile getFile();

  byte[] getBytes() throws IOException;

  FileType getContentType();

  void restoreOriginalContent(Project project);

  class MergeDocumentVersion implements MergeVersion {
    private static final Logger LOG = Logger.getInstance(MergeDocumentVersion.class);
    protected final Document myDocument;
    private final String myOriginalText;
    private String myTextBeforeMerge;

    public MergeDocumentVersion(Document document, String originalText) {
      LOG.assertTrue(originalText != null, "text should not be null");
      LOG.assertTrue(document != null, "document should not be null");
      LOG.assertTrue(document.isWritable(), "document should be writable");
      myDocument = document;
      myOriginalText = originalText;
    }

    public String getOriginalText() {
      return myOriginalText;
    }

    @Override
    public Document createWorkingDocument(final Project project) {
      //TODO[ik]: do we really need to create copy here?
      final Document workingDocument = myDocument; //DocumentUtil.createCopy(myDocument, project);
      //LOG.assertTrue(workingDocument != myDocument);
      workingDocument.setReadOnly(false);
      final DocumentReference ref = DocumentReferenceManager.getInstance().create(workingDocument);
      myTextBeforeMerge = myDocument.getText();
      ApplicationManager.getApplication().runWriteAction(() -> {
        setDocumentText(workingDocument, myOriginalText, DiffBundle.message("message.init.merge.content.command"), project);
        if (project != null) {
          final UndoManager undoManager = UndoManager.getInstance(project);
          if (undoManager != null) { //idea.sh merge command
            undoManager.nonundoableActionPerformed(ref, false);
          }
        }
      });
      return workingDocument;
    }

    @Override
    public void applyText(@NotNull final String text, final Project project) {
      ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, () ->
        doApplyText(text, project), DiffBundle.message("message.finish.merge.content.command"), null));
    }

    protected void doApplyText(@NotNull String text, Project project) {
      setDocumentText(myDocument, text, DiffBundle.message("save.merge.result.command.name"), project);

      FileDocumentManager.getInstance().saveDocument(myDocument);
    }

    @Override
    public void restoreOriginalContent(final Project project) {
      ApplicationManager.getApplication().runWriteAction(() -> doRestoreOriginalContent(project));
    }

    protected void doRestoreOriginalContent(@Nullable Project project) {
      setDocumentText(myDocument, myTextBeforeMerge, null, project);
    }

    @Override
    @Nullable
    public VirtualFile getFile() {
      return FileDocumentManager.getInstance().getFile(myDocument);
    }

    @Override
    public byte[] getBytes() throws IOException {
      VirtualFile file = getFile();
      return file != null ? file.contentsToByteArray() : myDocument.getText().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public FileType getContentType() {
      VirtualFile file = getFile();
      if (file == null) return FileTypes.PLAIN_TEXT;
      return file.getFileType();
    }

    private static void setDocumentText(@NotNull Document document,
                                        @NotNull @NonNls String text,
                                        @Nullable @NlsContexts.Command String commandName,
                                        @Nullable Project project) {
      CommandProcessor.getInstance().executeCommand(project, () ->
        document.replaceString(0, document.getTextLength(), StringUtil.convertLineSeparators(text)), commandName, null);
    }
  }
}
