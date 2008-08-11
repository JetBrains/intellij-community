package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class BraceHighlighter implements ProjectComponent {
  private final Project myProject;
  private final Alarm myAlarm = new Alarm();
  private CaretListener myCaretListener;
  private SelectionListener mySelectionListener;
  private DocumentListener myDocumentListener;
  private FocusChangeListener myFocusChangeListener;
  private boolean myIsDisposed = false;

  public BraceHighlighter(Project project) {
    myProject = project;
  }

  @NotNull
  public String getComponentName() {
    return "BraceHighlighter";
  }

  public void initComponent() { }

  public void disposeComponent() {
    myIsDisposed = true;
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

        myCaretListener = new CaretListener() {
          public void caretPositionChanged(CaretEvent e) {
            myAlarm.cancelAllRequests();
            Editor editor = e.getEditor();
            if (editor.getProject() == myProject) {
              updateBraces(editor, myAlarm);
            }
          }
        };
        eventMulticaster.addCaretListener(myCaretListener);

        mySelectionListener = new SelectionListener() {
          public void selectionChanged(SelectionEvent e) {
            myAlarm.cancelAllRequests();
            Editor editor = e.getEditor();
            if (editor.getProject() == myProject) {
              updateBraces(editor, myAlarm);
            }
          }
        };
        eventMulticaster.addSelectionListener(mySelectionListener);

        myDocumentListener = new DocumentAdapter() {
          public void documentChanged(DocumentEvent e) {
            myAlarm.cancelAllRequests();
            Editor[] editors = EditorFactory.getInstance().getEditors(e.getDocument(), myProject);
            for (Editor editor : editors) {
              updateBraces(editor, myAlarm);
            }
          }
        };
        eventMulticaster.addDocumentListener(myDocumentListener);

        myFocusChangeListener = new FocusChangeListener() {
          public void focusLost(Editor editor) {
            clearBraces(editor);
          }

          public void focusGained(Editor editor) {
            updateBraces(editor, myAlarm);
          }
        };
        ((EditorEventMulticasterEx)eventMulticaster).addFocusChangeListner(myFocusChangeListener);

        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);

        fileEditorManager.addFileEditorManagerListener(
          new FileEditorManagerAdapter() {
            public void selectionChanged(FileEditorManagerEvent e) {
              myAlarm.cancelAllRequests();
            }
          }
        );
      }
    });
  }

  static void updateBraces(final Editor editor, final Alarm alarm) {
    final Document document = editor.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;

    BraceHighlightingHandler.lookForInjectedAndMatchBracesInOtherThread(editor, alarm, new Processor<BraceHighlightingHandler>() {
      public boolean process(final BraceHighlightingHandler handler) {
        handler.updateBraces();
        return false;
      }
    });
  }

  private void clearBraces(final Editor editor) {
    BraceHighlightingHandler.lookForInjectedAndMatchBracesInOtherThread(editor, myAlarm, new Processor<BraceHighlightingHandler>() {
      public boolean process(final BraceHighlightingHandler handler) {
        handler.clearBraceHighlighters();
        return false;
      }
    });
  }

  public void projectClosed() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.removeCaretListener(myCaretListener);
    eventMulticaster.removeSelectionListener(mySelectionListener);
    eventMulticaster.removeDocumentListener(myDocumentListener);
    ((EditorEventMulticasterEx)eventMulticaster).removeFocusChangeListner(myFocusChangeListener);
  }
}
