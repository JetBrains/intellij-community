/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.MockDocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.PsiTreeChangeAdapter;

public class TypedAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actionSystem.TypedAction");

  private static final Object TYPING_COMMAND_GROUP = Key.create("Typing");

  private TypedActionHandler myHandler;
  private final static PsiTreeChangeListener myCommitLogger = new PsiModificationTracker();

  public TypedAction() {
    myHandler = new Handler();
  }

  private static class Handler implements TypedActionHandler {
    public void execute(Editor editor, char charTyped, DataContext dataContext) {
      if (editor.isViewer()) return;

      Document doc = editor.getDocument();
      if (!doc.isWritable()) {
        doc.fireReadOnlyModificationAttempt();
        return;
      }

      Project project = editor.getProject();
      doc.startGuardedBlockChecking();
      if (project != null) PsiManager.getInstance(project).addPsiTreeChangeListener(myCommitLogger);
      try {
        final String str = String.valueOf(charTyped);
        CommandProcessor.getInstance().setCurrentCommandName("Typing");
        final SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasBlockSelection()) {
          RangeMarker guard = selectionModel.getBlockSelectionGuard();
          if (guard != null) {
            DocumentEvent evt = new MockDocumentEvent(doc, editor.getCaretModel().getOffset());
            ReadOnlyFragmentModificationException e = new ReadOnlyFragmentModificationException(evt, guard);
            EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
            return;
          }

          final LogicalPosition start = selectionModel.getBlockStart();
          final LogicalPosition end = selectionModel.getBlockEnd();
          int column = Math.min(start.column, end.column);
          int startLine = Math.min(start.line, end.line);
          int endLine = Math.max(start.line, end.line);
          EditorModificationUtil.deleteBlockSelection(editor);
          for (int i = startLine; i <= endLine; i++) {
            editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(i, column));
            EditorModificationUtil.insertStringAtCaret(editor, str, true, true);
          }
          selectionModel.setBlockSelection(new LogicalPosition(startLine, column + 1),
                                           new LogicalPosition(endLine, column + 1));
          return;
        }

        EditorModificationUtil.insertStringAtCaret(editor, str, true, true);
      }
      catch (ReadOnlyFragmentModificationException e) {
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
      }
      finally {
        if (project != null) PsiManager.getInstance(project).removePsiTreeChangeListener(myCommitLogger);
        doc.stopGuardedBlockChecking();
      }
    }
  }

  public TypedActionHandler getHandler() {
    return myHandler;
  }

  public TypedActionHandler setupHandler(TypedActionHandler handler) {
    TypedActionHandler tmp = myHandler;
    myHandler = handler;
    return tmp;
  }

  public final void actionPerformed(final Editor editor, final char charTyped, final DataContext dataContext) {
    if (editor == null) return;

    Runnable command = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Document doc = editor.getDocument();
            doc.startGuardedBlockChecking();
            try {
              getHandler().execute(editor, charTyped, dataContext);
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
    };

    CommandProcessor.getInstance().executeCommand((Project)dataContext.getData(DataConstants.PROJECT), command, "", TYPING_COMMAND_GROUP);
  }

  private static class PsiModificationTracker extends PsiTreeChangeAdapter {
    public void beforeChildAddition(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforeChildRemoval(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforeChildReplacement(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforeChildMovement(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforeChildrenChange(PsiTreeChangeEvent event) {
      logError();
    }

    public void beforePropertyChange(PsiTreeChangeEvent event) {
      logError();
    }
    private void logError() {
      LOG.error("PSI should not be commited on every typing since this greatly reduces app responsiveness");
    }
  }
}
