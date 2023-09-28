// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightingSessionImpl;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

/**
 * Listens for editor events and starts brace/identifier highlighting in the background
 */
final class BackgroundHighlighter implements StartupActivity, DumbAware {
  private final Alarm myAlarm = new Alarm();

  @Override
  public void runActivity(@NotNull Project project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode() ||
        // sorry upsource
        !IdentifierHighlighterPassFactory.isEnabled()) {
      return;
    }

    Disposable activityDisposable = ExtensionPointUtil.createExtensionDisposable(this, StartupActivity.Companion.getPOST_STARTUP_ACTIVITY());
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
          // Don't update braces in case of active/absent selection.
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
        EditorFactory.getInstance().editors(e.getDocument(), project).forEach(
          editor -> submitUpdateHighlighted(project, editor)
        );
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

    project.getMessageBus().connect(parentDisposable)
      .subscribe(TemplateManager.TEMPLATE_STARTED_TOPIC, state -> {
        if (state.isFinished()) return;
        updateHighlighted(project, state.getEditor());
        state.addTemplateStateListener(new TemplateEditingAdapter() {
          @Override
          public void templateFinished(@NotNull Template template, boolean brokenOff) {
            updateHighlighted(project, state.getEditor());
          }
        });
      });
  }

  private void onCaretUpdate(@NotNull Editor editor, @NotNull Project project) {
    myAlarm.cancelAllRequests();
    SelectionModel selectionModel = editor.getSelectionModel();
    // Don't update braces in case of the active selection.
    if (editor.getProject() != project || selectionModel.hasSelection()) {
      return;
    }
    submitUpdateHighlighted(project, editor);
  }

  private void submitUpdateHighlighted(@NotNull Project project, @NotNull Editor editor) {
    updateHighlighted(project, editor);
  }

  private void updateHighlighted(@NotNull Project project, @NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    if (editor.getDocument().isInBulkUpdate()) {
      return;
    }
    if (!BackgroundHighlightingUtil.isValidEditor(editor)) {
      return;
    }

    BackgroundHighlightingUtil.lookForInjectedFileInOtherThread(
      project, editor,
      (newFile, newEditor) -> {
        int offsetBefore = editor.getCaretModel().getOffset();

        submitIdentifierHighlighterPass(editor, offsetBefore, newFile, newEditor);
        return HeavyBraceHighlighter.match(newFile, offsetBefore);
      },
      (newFile, newEditor, maybeMatch) -> {
        BraceHighlightingHandler handler = new BraceHighlightingHandler(project, newEditor, myAlarm, newFile);
        if (maybeMatch == null) {
          handler.updateBraces();
        }
        else {
          CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

          if (BackgroundHighlightingUtil.needMatching(newEditor, codeInsightSettings)) {
            FileType fileType = PsiUtilBase.getPsiFileAtOffset(newFile, maybeMatch.first.getStartOffset()).getFileType();
            handler.clearBraceHighlighters();
            handler.highlightBraces(maybeMatch.first, maybeMatch.second, true, false, fileType);
          }
        }
      });
  }

  private void clearBraces(@NotNull Project project, @NotNull Editor editor) {
    BackgroundHighlightingUtil.lookForInjectedFileInOtherThread(project, editor, (__, ___) -> null, (foundFile, newEditor, __) -> {
      BraceHighlightingHandler handler = new BraceHighlightingHandler(project, newEditor, myAlarm, foundFile);
      handler.clearBraceHighlighters();
    });
  }

  private static void submitIdentifierHighlighterPass(@NotNull Editor hostEditor,
                                                      int offsetBefore,
                                                      @NotNull PsiFile newFile,
                                                      @NotNull Editor newEditor) {
    ReadAction.nonBlocking(() -> {
        int textLength = newFile.getTextLength();
        if (textLength == -1 | hostEditor.isDisposed()) {
          // sometimes some crazy stuff is returned (EA-248725)
          return null;
        }
        ProperTextRange visibleRange = ProperTextRange.from(0, textLength);
        IdentifierHighlighterPass pass = new IdentifierHighlighterPassFactory().createHighlightingPass(newFile, newEditor, visibleRange);
        DaemonProgressIndicator indicator = new DaemonProgressIndicator();
        ProgressIndicatorUtils.runWithWriteActionPriority(() -> {
          PsiFile hostPsiFile = PsiDocumentManager.getInstance(newFile.getProject()).getPsiFile(hostEditor.getDocument());
          if (hostPsiFile == null) return;
          HighlightingSessionImpl.runInsideHighlightingSession(hostPsiFile, hostEditor.getColorsScheme(), ProperTextRange.create(hostPsiFile.getTextRange()), false, session -> {
            if (pass != null) {
              pass.doCollectInformation(session);
            }
          });
        }, indicator);
        return pass;
      })
      .expireWhen(() -> !BackgroundHighlightingUtil.isValidEditor(hostEditor) ||
                        hostEditor.getCaretModel().getOffset() != offsetBefore)
      .coalesceBy(HighlightIdentifiersKey.class, hostEditor)
      .finishOnUiThread(ModalityState.stateForComponent(hostEditor.getComponent()), pass -> {
        if (pass != null) {
          pass.doAdditionalCodeBlockHighlighting();
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  static Alarm getAlarm() {
    return Objects.requireNonNull(StartupActivity.Companion.getPOST_STARTUP_ACTIVITY().findExtension(BackgroundHighlighter.class)).myAlarm;
  }

  @TestOnly
  static void enableListenersInTest(@NotNull Project project, @NotNull Disposable disposable) {
    StartupActivity.Companion.getPOST_STARTUP_ACTIVITY().findExtension(BackgroundHighlighter.class).registerListeners(project, disposable);
  }

  private static final class HighlightIdentifiersKey {
  }
}
