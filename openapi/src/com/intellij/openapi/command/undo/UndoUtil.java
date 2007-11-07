/*
 * @author max
 */
package com.intellij.openapi.command.undo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;

public class UndoUtil {
  private UndoUtil() {}

  /**
   * make undoable action in current document in order to Undo action work from current file
   * @param file to make editors of to respond to undo action.
   */
  public static void markPsiFileForUndo(final PsiFile file) {
    Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;
    final DocumentReference ref = DocumentReferenceByDocument.createDocumentReference(document);
    UndoManager.getInstance(project).undoableActionPerformed(new UndoableAction() {
      public void undo() {
      }

      public void redo() {
      }

      public DocumentReference[] getAffectedDocuments() {
        return new DocumentReference[]{ref};
      }

      public boolean isComplex() {
        return false;
      }

      @NonNls
      public String toString() {
        return "markDocumentForUndo: " + file;
      }
    });
  }
}