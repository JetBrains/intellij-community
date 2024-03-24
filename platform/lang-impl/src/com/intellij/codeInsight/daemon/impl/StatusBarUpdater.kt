// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.SingleAlarm;
import org.jetbrains.annotations.NotNull;

final class StatusBarUpdater implements Disposable {
  private final Project myProject;
  private final SingleAlarm myAlarm;

  StatusBarUpdater(Project project) {
    myProject = project;
    myAlarm = new SingleAlarm(() -> updateStatus(), 100, this);

    project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        updateLater();
      }
    });

    project.getMessageBus().connect(this).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
      @Override
      public void daemonFinished() {
        updateLater();
      }
    });
  }

  private void updateLater() {
    myAlarm.cancelAndRequest();
  }

  @Override
  public void dispose() {
  }

  private static final HighlightSeverity MIN = new HighlightSeverity("min", HighlightSeverity.INFORMATION.myVal + 1);
  private void updateStatus() {
    Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor == null || !editor.getContentComponent().hasFocus()){
      return;
    }

    Document document = editor.getDocument();
    if (document.isInBulkUpdate()) return;

    int offset = editor.getCaretModel().getOffset();
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    HighlightInfo info = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(document, offset, false, MIN);
    String text = info != null && info.getDescription() != null ? info.getDescription() : "";

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(editor.getContentComponent(), myProject);
    if (statusBar != null && !text.equals(statusBar.getInfo())) {
      statusBar.setInfo(text, "updater");
    }
  }
}
