// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class BraceHighlighter implements StartupActivity {

  private final Alarm myAlarm = new Alarm();

  @Override
  public void runActivity(@NotNull final Project project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return; // sorry, upsource
    }

    MessageBusConnection busConnection = project.getMessageBus().connect();
    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent e) {
        myAlarm.cancelAllRequests();
        FileEditor oldEditor = e.getOldEditor();
        if (oldEditor instanceof TextEditor) {
          clearBraces(((TextEditor)oldEditor).getEditor());
        }
        FileEditor newEditor = e.getNewEditor();
        if (newEditor instanceof TextEditor) {
          updateBraces(((TextEditor)newEditor).getEditor(), myAlarm);
        }
      }
    });
    busConnection.subscribe(EditorEventMulticaster.TOPIC, new EditorEventMulticaster.EditorEventListener() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        myAlarm.cancelAllRequests();
        Editor editor = e.getEditor();
        final SelectionModel selectionModel = editor.getSelectionModel();
        // Don't update braces in case of the active selection.
        if (editor.getProject() != project || selectionModel.hasSelection()) {
          return;
        }

        final Document document = editor.getDocument();
        int line = e.getNewPosition().line;
        if (line < 0 || line >= document.getLineCount()) {
          return;
        }
        updateBraces(editor, myAlarm);
      }

      @Override
      public void selectionChanged(@NotNull SelectionEvent e) {
        myAlarm.cancelAllRequests();
        Editor editor = e.getEditor();
        if (editor.getProject() != project) {
          return;
        }

        final TextRange oldRange = e.getOldRange();
        final TextRange newRange = e.getNewRange();
        if (oldRange != null && newRange != null && !(oldRange.isEmpty() ^ newRange.isEmpty())) {
          // Don't perform braces update in case of active/absent selection.
          return;
        }
        updateBraces(editor, myAlarm);
      }

      @Override
      public void documentChanged(DocumentEvent e) {
        myAlarm.cancelAllRequests();
        Editor[] editors = EditorFactory.getInstance().getEditors(e.getDocument(), project);
        for (Editor editor : editors) {
          updateBraces(editor, myAlarm);
        }
      }
    });
  }

  static void updateBraces(@NotNull final Editor editor, @NotNull final Alarm alarm) {
    final Document document = editor.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;

    BraceHighlightingHandler.lookForInjectedAndMatchBracesInOtherThread(editor, alarm, handler -> {
      handler.updateBraces();
      return false;
    });
  }

  private void clearBraces(@NotNull final Editor editor) {
    BraceHighlightingHandler.lookForInjectedAndMatchBracesInOtherThread(editor, myAlarm, handler -> {
      handler.clearBraceHighlighters();
      return false;
    });
  }
}
