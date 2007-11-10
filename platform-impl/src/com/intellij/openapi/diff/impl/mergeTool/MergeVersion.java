package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.command.undo.NonUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public interface MergeVersion {
  Document createWorkingDocument(Project project);

  void applyText(String text, Project project);

  VirtualFile getFile();

  byte[] getBytes() throws IOException;

  FileType getContentType();

  class MergeDocumentVersion implements MergeVersion {
    private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.mergeTool.MergeVersion.MergeDocumentVersion");
    private final Document myDocument;
    private final String myOriginalText;

    public MergeDocumentVersion(Document document, String originalText) {
      LOG.assertTrue(originalText != null, "text should not be null");
      LOG.assertTrue(document != null, "document should not be null");
      LOG.assertTrue(document.isWritable(), "document should be writable");
      myDocument = document;
      myOriginalText = originalText;
    }

    public Document createWorkingDocument(final Project project) {
      //TODO[ik]: do we really need to create copy here?
      final Document workingDocument = myDocument; //DocumentUtil.createCopy(myDocument, project);
      //LOG.assertTrue(workingDocument != myDocument);
      workingDocument.setReadOnly(false);
      final DocumentReference ref = DocumentReferenceByDocument.createDocumentReference(workingDocument);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          setDocumentText(workingDocument, myOriginalText, DiffBundle.message("merge.init.merge.content.command.name"), project);
          UndoManager.getInstance(project).undoableActionPerformed(new NonUndoableAction() {
            public boolean isComplex() {
              return false;
            }

            public DocumentReference[] getAffectedDocuments() {
              return new DocumentReference[]{ref};
            }
          });
        }
      });
      return workingDocument;
    }

    public void applyText(final String text, final Project project) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          setDocumentText(myDocument, text, DiffBundle.message("save.merge.result.command.name"), project);

          FileDocumentManager.getInstance().saveDocument(myDocument);
          final VirtualFile file = getFile();
          if (file != null) {
            final FileType fileType = file.getFileType();
            if (fileType == StdFileTypes.IDEA_MODULE || fileType == StdFileTypes.IDEA_PROJECT || fileType == StdFileTypes.IDEA_WORKSPACE) {
              ProjectManagerEx.getInstanceEx().saveChangedProjectFile(file, project);
            }
          }
          
        }
      });
    }

    public VirtualFile getFile() {
      return FileDocumentManager.getInstance().getFile(myDocument);
    }

    public byte[] getBytes() throws IOException {
      VirtualFile file = getFile();
      if (file != null) return file.contentsToByteArray();
      return myDocument.getText().getBytes();
    }

    public FileType getContentType() {
      VirtualFile file = getFile();
      if (file == null) return StdFileTypes.PLAIN_TEXT;
      return FileTypeManager.getInstance().getFileTypeByFile(file);
    }

    private void setDocumentText(final Document document, final String startingText, String name, Project project) {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          document.replaceString(0, document.getTextLength(), startingText);
        }
      }, name, null);
    }
  }
}
