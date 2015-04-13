/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class BraceHighlighter implements StartupActivity {

  private final Alarm myAlarm = new Alarm();

  @Override
  public void runActivity(@NotNull final Project project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return; // sorry, upsource
    final EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    CaretListener myCaretListener = new CaretAdapter() {
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
    };
    eventMulticaster.addCaretListener(myCaretListener, project);

    final SelectionListener mySelectionListener = new SelectionListener() {
      @Override
      public void selectionChanged(SelectionEvent e) {
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
    };
    eventMulticaster.addSelectionListener(mySelectionListener, project);

    DocumentListener documentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        myAlarm.cancelAllRequests();
        Editor[] editors = EditorFactory.getInstance().getEditors(e.getDocument(), project);
        for (Editor editor : editors) {
          updateBraces(editor, myAlarm);
        }
      }
    };
    eventMulticaster.addDocumentListener(documentListener, project);

    final FocusChangeListener myFocusChangeListener = new FocusChangeListener() {
      @Override
      public void focusLost(Editor editor) {
        clearBraces(editor);
      }

      @Override
      public void focusGained(Editor editor) {
        updateBraces(editor, myAlarm);
      }
    };
    ((EditorEventMulticasterEx)eventMulticaster).addFocusChangeListner(myFocusChangeListener, project);

    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

    fileEditorManager.addFileEditorManagerListener(new FileEditorManagerAdapter() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent e) {
        myAlarm.cancelAllRequests();
      }
    }, project);
  }

  static void updateBraces(@NotNull final Editor editor, @NotNull final Alarm alarm) {
    final Document document = editor.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;

    BraceHighlightingHandler.lookForInjectedAndMatchBracesInOtherThread(editor, alarm, new Processor<BraceHighlightingHandler>() {
      @Override
      public boolean process(final BraceHighlightingHandler handler) {
        handler.updateBraces();
        return false;
      }
    });
  }

  private void clearBraces(@NotNull final Editor editor) {
    BraceHighlightingHandler.lookForInjectedAndMatchBracesInOtherThread(editor, myAlarm, new Processor<BraceHighlightingHandler>() {
      @Override
      public boolean process(final BraceHighlightingHandler handler) {
        handler.clearBraceHighlighters();
        return false;
      }
    });
  }
}
