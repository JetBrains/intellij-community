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
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;
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

  public BraceHighlighter(Project project) {
    myProject = project;
  }

  @NotNull
  public String getComponentName() {
    return "BraceHighlighter";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
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
        eventMulticaster.addCaretListener(myCaretListener, myProject);

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
        eventMulticaster.addDocumentListener(myDocumentListener, myProject);

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

        Disposer.register(myProject, new Disposable() {
          public void dispose() {
            EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
            eventMulticaster.removeSelectionListener(mySelectionListener);
            ((EditorEventMulticasterEx)eventMulticaster).removeFocusChangeListner(myFocusChangeListener);
          }
        });
      }
    });
  }

  static void updateBraces(@NotNull final Editor editor, @NotNull final Alarm alarm) {
    final Document document = editor.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;

    BraceHighlightingHandler.lookForInjectedAndMatchBracesInOtherThread(editor, alarm, new Processor<BraceHighlightingHandler>() {
      public boolean process(final BraceHighlightingHandler handler) {
        handler.updateBraces();
        return false;
      }
    });
  }

  private void clearBraces(@NotNull final Editor editor) {
    BraceHighlightingHandler.lookForInjectedAndMatchBracesInOtherThread(editor, myAlarm, new Processor<BraceHighlightingHandler>() {
      public boolean process(final BraceHighlightingHandler handler) {
        handler.clearBraceHighlighters();
        return false;
      }
    });
  }

  public void projectClosed() {
  }
}
