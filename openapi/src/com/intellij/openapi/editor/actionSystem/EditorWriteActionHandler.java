package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.MockDocumentEvent;

public abstract class EditorWriteActionHandler extends EditorActionHandler {
  public final void execute(final Editor editor, final DataContext dataContext) {
    if (editor.isViewer()) return;

    if (!editor.getDocument().isWritable()) {
      editor.getDocument().fireReadOnlyModificationAttempt();
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasBlockSelection()) {
          RangeMarker guard = selectionModel.getBlockSelectionGuard();
          if (guard != null) {
            DocumentEvent evt = new MockDocumentEvent(editor.getDocument(), editor.getCaretModel().getOffset());
            ReadOnlyFragmentModificationException e = new ReadOnlyFragmentModificationException(evt, guard);
            EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
            return;
          }
        }

        Document doc = editor.getDocument();
        doc.startGuardedBlockChecking();
        try {
          executeWriteAction(editor, dataContext);
        }
        catch (ReadOnlyFragmentModificationException e) {
          EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
        }
        finally {
          doc.stopGuardedBlockChecking();
        }
      }
    });
  }

  public abstract void executeWriteAction(Editor editor, DataContext dataContext);
}
