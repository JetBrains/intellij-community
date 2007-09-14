package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.Alarm;
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
              updateBraces(editor);
            }
          }
        };
        eventMulticaster.addCaretListener(myCaretListener);

        mySelectionListener = new SelectionListener() {
          public void selectionChanged(SelectionEvent e) {
            myAlarm.cancelAllRequests();
            Editor editor = e.getEditor();
            if (editor.getProject() == myProject) {
              updateBraces(editor);
            }
          }
        };
        eventMulticaster.addSelectionListener(mySelectionListener);

        myDocumentListener = new DocumentAdapter() {
          public void documentChanged(DocumentEvent e) {
            myAlarm.cancelAllRequests();
            Editor[] editors = EditorFactory.getInstance().getEditors(e.getDocument(), myProject);
            for (Editor editor : editors) {
              updateBraces(editor);
            }
          }
        };
        eventMulticaster.addDocumentListener(myDocumentListener);

        myFocusChangeListener = new FocusChangeListener() {
          public void focusLost(Editor editor) {
            clearBraces(editor);
          }

          public void focusGained(Editor editor) {
            updateBraces(editor);
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

  private void updateBraces(final Editor editor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (!myIsDisposed && !myProject.isDisposed() && editor.getComponent().isShowing() && !editor.isViewer()) {
            new BraceHighlightingHandler(myProject, editor, myAlarm).updateBraces();
          }
        }
      }, ModalityState.stateForComponent(editor.getComponent()));
  }

  private void clearBraces(final Editor editor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (!myIsDisposed && editor.getComponent().isShowing()) {
            new BraceHighlightingHandler(myProject, editor, myAlarm).clearBraceHighlighters();
          }
        }
      }, ModalityState.stateForComponent(editor.getComponent()));
  }

  public void projectClosed() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.removeCaretListener(myCaretListener);
    eventMulticaster.removeSelectionListener(mySelectionListener);
    eventMulticaster.removeDocumentListener(myDocumentListener);
    ((EditorEventMulticasterEx)eventMulticaster).removeFocusChangeListner(myFocusChangeListener);
  }

}
