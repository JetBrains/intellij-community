/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.components.AbstractProjectComponent;
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
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class BraceHighlighter extends AbstractProjectComponent {
  private final Alarm myAlarm = new Alarm();

  public BraceHighlighter(Project project) {
    super(project);
  }

  @NotNull
  public String getComponentName() {
    return "BraceHighlighter";
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        doinit();
      }
    });
  }

  private void doinit() {
    final EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    CaretListener myCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        myAlarm.cancelAllRequests();
        Editor editor = e.getEditor();
        final SelectionModel selectionModel = editor.getSelectionModel();
        // Don't update braces in case of the active selection.
        if (editor.getProject() != myProject || selectionModel.hasSelection() || selectionModel.hasBlockSelection()) {
          return;
        }

        final Document document = editor.getDocument();
        int line = e.getNewPosition().line;
        // Don't update braces for virtual space navigation.
        if (line < 0 || line >= document.getLineCount() || editor.getCaretModel().getOffset() >= document.getLineEndOffset(line)) {
          return;
        }
        updateBraces(editor, myAlarm);
      }
    };
    eventMulticaster.addCaretListener(myCaretListener, myProject);

    final SelectionListener mySelectionListener = new SelectionListener() {
      public void selectionChanged(SelectionEvent e) {
        myAlarm.cancelAllRequests();
        Editor editor = e.getEditor();
        if (editor.getProject() != myProject) {
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
    eventMulticaster.addSelectionListener(mySelectionListener, myProject);

    DocumentListener documentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        myAlarm.cancelAllRequests();
        Editor[] editors = EditorFactory.getInstance().getEditors(e.getDocument(), myProject);
        for (Editor editor : editors) {
          updateBraces(editor, myAlarm);
        }
      }
    };
    eventMulticaster.addDocumentListener(documentListener, myProject);

    final FocusChangeListener myFocusChangeListener = new FocusChangeListener() {
      public void focusLost(Editor editor) {
        clearBraces(editor);
      }

      public void focusGained(Editor editor) {
        updateBraces(editor, myAlarm);
      }
    };
    ((EditorEventMulticasterEx)eventMulticaster).addFocusChangeListner(myFocusChangeListener, myProject);

    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);

    fileEditorManager.addFileEditorManagerListener(new FileEditorManagerAdapter() {
      public void selectionChanged(FileEditorManagerEvent e) {
        myAlarm.cancelAllRequests();
      }
    }, myProject);
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
}
