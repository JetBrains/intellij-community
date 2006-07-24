
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;

public class StatusBarUpdater {
  private final Project myProject;

  private String myLastStatusText;
  private CaretListener myCaretListener;

  public StatusBarUpdater(Project project) {
    myProject = project;

    myCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (!myProject.isOpen()) return;
            updateStatus();
          }
        });
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addCaretListener(myCaretListener);

    FileEditorManager.getInstance(myProject).addFileEditorManagerListener(
      new FileEditorManagerAdapter() {
        public void selectionChanged(FileEditorManagerEvent e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              if (myProject.isOpen()) {
                updateStatus();
              }
            }
          });
        }
      }
    );
  }

  public void dispose() {
    EditorFactory.getInstance().getEventMulticaster().removeCaretListener(myCaretListener);
  }

  public void updateStatus() {
    Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor == null || !editor.getContentComponent().hasFocus()){
      return;
    }

    int offset = editor.getCaretModel().getOffset();
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    HighlightInfo info = codeAnalyzer.findHighlightByOffset(editor.getDocument(), offset, false);
    String text;
    if (info != null && info.description != null) {
      text = info.description;
    }
    else{
      text = "";
    }

    if (!text.equals(myLastStatusText)){
      WindowManager.getInstance().getStatusBar(myProject).setInfo(text);
      myLastStatusText = text;
    }
  }
}