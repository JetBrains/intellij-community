
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;

public class StatusBarUpdater implements Disposable {
  private final Project myProject;
  private final UpdateStatusRunnable myUpdateStatusRunnable = new UpdateStatusRunnable();

  public StatusBarUpdater(Project project) {
    myProject = project;

    CaretListener caretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        ApplicationManager.getApplication().invokeLater(myUpdateStatusRunnable);
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addCaretListener(caretListener, this);

    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void selectionChanged(FileEditorManagerEvent event) {
        ApplicationManager.getApplication().invokeLater(myUpdateStatusRunnable);
      }
    });
  }

  public void dispose() {
  }

  public void updateStatus() {
    Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor == null || !editor.getContentComponent().hasFocus()){
      return;
    }

    final Document document = editor.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;

    int offset = editor.getCaretModel().getOffset();
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    HighlightInfo info = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(document, offset, false);
    String text = info != null && info.description != null ? info.description : "";

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (statusBar instanceof StatusBarEx) {
      StatusBarEx barEx = (StatusBarEx)statusBar;
      if (!text.equals(barEx.getInfo())){
        statusBar.setInfo(text);
      }
    }
  }

  private class UpdateStatusRunnable implements DumbAwareRunnable {
    public void run() {
      if (!myProject.isDisposed()) {
        updateStatus();
      }
    }
  }
}