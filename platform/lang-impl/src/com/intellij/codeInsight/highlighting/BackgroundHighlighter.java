// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

/**
 * Listens for editor events and starts brace/identifier highlighting in the background
 */
final class BackgroundHighlighter implements StartupActivity.DumbAware {
  private final Alarm myAlarm = new Alarm();

  @Override
  public void runActivity(@NotNull Project project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && !IdentifierHighlighterPassFactory.isEnabledInHeadlessMode()) return; // sorry, upsource

    Disposable activityDisposable = ExtensionPointUtil.createExtensionDisposable(this, StartupActivity.POST_STARTUP_ACTIVITY);
    Disposer.register(project, activityDisposable);

    registerListeners(project, activityDisposable);
  }

  private void registerListeners(@NotNull Project project, @NotNull Disposable parentDisposable) {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        if (e.getCaret() != e.getEditor().getCaretModel().getPrimaryCaret()) return;
        onCaretUpdate(e.getEditor(), project);
      }

      @Override
      public void caretAdded(@NotNull CaretEvent e) {
        if (e.getCaret() != e.getEditor().getCaretModel().getPrimaryCaret()) return;
        onCaretUpdate(e.getEditor(), project);
      }

      @Override
      public void caretRemoved(@NotNull CaretEvent e) {
        onCaretUpdate(e.getEditor(), project);
      }
    }, parentDisposable);

    SelectionListener selectionListener = new SelectionListener() {
      @Override
      public void selectionChanged(@NotNull SelectionEvent e) {
        myAlarm.cancelAllRequests();
        Editor editor = e.getEditor();
        if (editor.getProject() != project) {
          return;
        }

        TextRange oldRange = e.getOldRange();
        TextRange newRange = e.getNewRange();
        if (oldRange != null && newRange != null && oldRange.isEmpty() == newRange.isEmpty()) {
          // Don't perform braces update in case of active/absent selection.
          return;
        }
        updateHighlighted(project, editor);
      }
    };
    eventMulticaster.addSelectionListener(selectionListener, parentDisposable);

    DocumentListener documentListener = new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        myAlarm.cancelAllRequests();
        EditorFactory.getInstance().editors(e.getDocument(), project).forEach(editor -> updateHighlighted(project, editor));
      }
    };
    eventMulticaster.addDocumentListener(documentListener, parentDisposable);

    project.getMessageBus().connect(parentDisposable)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
        @Override
        public void selectionChanged(@NotNull FileEditorManagerEvent e) {
          myAlarm.cancelAllRequests();
          FileEditor oldEditor = e.getOldEditor();
          if (oldEditor instanceof TextEditor) {
            clearBraces(project, ((TextEditor)oldEditor).getEditor());
          }
          FileEditor newEditor = e.getNewEditor();
          if (newEditor instanceof TextEditor) {
            updateHighlighted(project, ((TextEditor)newEditor).getEditor());
          }
        }
      });
  }

  private void onCaretUpdate(@NotNull Editor editor, @NotNull Project project) {
    myAlarm.cancelAllRequests();
    SelectionModel selectionModel = editor.getSelectionModel();
    // Don't update braces in case of the active selection.
    if (editor.getProject() != project || selectionModel.hasSelection()) {
      return;
    }
    updateHighlighted(project, editor);
  }

  private void updateHighlighted(@NotNull Project project, @NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (editor.getDocument().isInBulkUpdate()) {
      return;
    }

    BackgroundHighlightingUtil.lookForInjectedFileInOtherThread(project, editor, (foundFile, newEditor)->{
      IdentifierHighlighterPass pass = new IdentifierHighlighterPassFactory().
        createHighlightingPass(foundFile, newEditor, foundFile.getTextRange());
      if (pass != null) {
        pass.doCollectInformation();
      }
      return pass;
    }, (foundFile, newEditor, pass) -> {
      BraceHighlightingHandler handler = new BraceHighlightingHandler(project, newEditor, myAlarm, foundFile);
      handler.updateBraces();

      if (pass != null) {
        pass.doApplyInformationToEditor();
      }
    });
  }

  private void clearBraces(@NotNull Project project, @NotNull Editor editor) {
    BackgroundHighlightingUtil.lookForInjectedFileInOtherThread(project, editor, (__,___)->null, (foundFile, newEditor,__) -> {
      BraceHighlightingHandler handler = new BraceHighlightingHandler(project, newEditor, myAlarm, foundFile);
      handler.clearBraceHighlighters();
    });
  }

  @NotNull
  static Alarm getAlarm() {
    return Objects.requireNonNull(POST_STARTUP_ACTIVITY.findExtension(BackgroundHighlighter.class)).myAlarm;
  }

  @TestOnly
  static void enableListenersInTest(@NotNull Project project, @NotNull Disposable disposable) {
    POST_STARTUP_ACTIVITY.findExtension(BackgroundHighlighter.class).registerListeners(project, disposable);
  }
}
