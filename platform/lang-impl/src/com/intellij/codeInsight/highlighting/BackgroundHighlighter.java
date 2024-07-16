// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightingSessionImpl;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerUtilBase;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * Listens for editor events and starts brace/identifier highlighting in the background
 */
final class BackgroundHighlighter {
  private static final Key<Collection<RangeHighlighter>> SELECTION_HIGHLIGHTS = new Key<>("SELECTION_HIGHLIGHTS");
  private static final Key<String> HIGHLIGHTED_TEXT = new Key<>("HIGHLIGHTED_TEXT");
  private final Alarm alarm = new Alarm();

  public void runActivity(@NotNull Project project) {
    Disposable parentDisposable = ExtensionPointUtil.createExtensionDisposable(this, StartupActivity.Companion.getPOST_STARTUP_ACTIVITY());
    Disposer.register(project, parentDisposable);

    registerListeners(project, parentDisposable, alarm);
  }

  static void registerListeners(@NotNull Project project, @NotNull Disposable parentDisposable, @NotNull Alarm alarm) {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        if (e.getCaret() != e.getEditor().getCaretModel().getPrimaryCaret()) return;
        onCaretUpdate(e.getEditor(), project, alarm);
      }

      @Override
      public void caretAdded(@NotNull CaretEvent e) {
        if (e.getCaret() != e.getEditor().getCaretModel().getPrimaryCaret()) return;
        onCaretUpdate(e.getEditor(), project, alarm);
      }

      @Override
      public void caretRemoved(@NotNull CaretEvent e) {
        onCaretUpdate(e.getEditor(), project, alarm);
      }
    }, parentDisposable);

    SelectionListener selectionListener = new SelectionListener() {
      @Override
      public void selectionChanged(@NotNull SelectionEvent e) {
        alarm.cancelAllRequests();
        Editor editor = e.getEditor();
        if (editor.getProject() != project) {
          return;
        }

        if (!highlightSelection(project, editor)) {
          removeSelectionHighlights(editor);
        }

        TextRange oldRange = e.getOldRange();
        TextRange newRange = e.getNewRange();
        if (oldRange != null && newRange != null && oldRange.isEmpty() == newRange.isEmpty()) {
          // Don't update braces in case of active/absent selection.
          return;
        }
        updateHighlighted(project, editor, alarm);
      }
    };
    eventMulticaster.addSelectionListener(selectionListener, parentDisposable);

    DocumentListener documentListener = new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        alarm.cancelAllRequests();
        EditorFactory.getInstance().editors(e.getDocument(), project).forEach(
          editor -> updateHighlighted(project, editor, alarm)
        );
      }
    };
    eventMulticaster.addDocumentListener(documentListener, parentDisposable);

    MessageBusConnection connection = project.getMessageBus().connect(parentDisposable);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent e) {
        alarm.cancelAllRequests();
        FileEditor oldEditor = e.getOldEditor();
        if (oldEditor instanceof TextEditor) {
          clearBraces(project, ((TextEditor)oldEditor).getEditor(), alarm);
        }
        FileEditor newEditor = e.getNewEditor();
        if (newEditor instanceof TextEditor textEditor) {
          Editor editor = textEditor.getEditor();
          updateHighlighted(project, editor, alarm);
          if (!highlightSelection(project, editor)) {
            removeSelectionHighlights(editor);
          }
        }
      }
    });

    connection.subscribe(TemplateManager.TEMPLATE_STARTED_TOPIC, state -> {
      if (state.isFinished()) return;
      updateHighlighted(project, state.getEditor(), alarm);
      state.addTemplateStateListener(new TemplateEditingAdapter() {
        @Override
        public void templateFinished(@NotNull Template template, boolean brokenOff) {
          updateHighlighted(project, state.getEditor(), alarm);
        }
      });
    });
  }

  private static void onCaretUpdate(@NotNull Editor editor, @NotNull Project project, @NotNull Alarm alarm) {
    alarm.cancelAllRequests();
    SelectionModel selectionModel = editor.getSelectionModel();
    // Don't update braces in case of the active selection.
    if (editor.getProject() != project || selectionModel.hasSelection()) {
      return;
    }
    updateHighlighted(project, editor, alarm);
  }

  private static boolean highlightSelection(@NotNull Project project, @NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    Document document = editor.getDocument();
    long stamp = document.getModificationStamp();
    if (document.isInBulkUpdate() || !BackgroundHighlightingUtil.isValidEditor(editor)) {
      return false;
    }
    if (!editor.getSettings().isHighlightSelectionOccurrences()) {
      return false;
    }
    if (TemplateManagerUtilBase.getTemplateState(editor) != null) {
      return false; // don't highlight selected text when template is active
    }
    CaretModel caretModel = editor.getCaretModel();
    if (caretModel.getCaretCount() > 1) {
      return false;
    }
    Caret caret = caretModel.getPrimaryCaret();
    if (!caret.hasSelection()) {
      return false;
    }
    int start = caret.getSelectionStart();
    int end = caret.getSelectionEnd();
    CharSequence sequence = document.getCharsSequence();
    String toFind = sequence.subSequence(start, end).toString();
    if (toFind.trim().isEmpty() || toFind.contains("\n")) {
      return false;
    }
    String previous = editor.getUserData(HIGHLIGHTED_TEXT);
    if (toFind.equals(previous)) {
      return true;
    }
    editor.putUserData(HIGHLIGHTED_TEXT, toFind);
    FindManager findManager = FindManager.getInstance(project);
    FindModel findModel = new FindModel();
    findModel.copyFrom(findManager.getFindInFileModel());
    findModel.setRegularExpressions(false);
    findModel.setStringToFind(toFind);
    int threshold = Registry.intValue("editor.highlight.selected.text.max.occurrences.threshold", 50);
    ReadAction.nonBlocking(() -> {
        FindResult result = findManager.findString(sequence, 0, findModel, null);
        List<FindResult> results = new ArrayList<>();
        int count = 0;
        while (result.isStringFound() && count < LivePreviewController.MATCHES_LIMIT) {
          count++;
          if (count > threshold) {
            return Collections.<FindResult>emptyList();
          }
          results.add(result);
          result = findManager.findString(sequence, result.getEndOffset(), findModel);
        }
        return results;
      })
      .coalesceBy(HighlightSelectionKey.class, editor)
      .expireWhen(() -> document.getModificationStamp() != stamp || editor.isDisposed())
      .finishOnUiThread(ModalityState.nonModal(), results -> {
        if (document.getModificationStamp() != stamp || results.isEmpty()) {
          return;
        }
        removeSelectionHighlights(editor);
        List<RangeHighlighter> highlighters = new ArrayList<>();
        MarkupModel markupModel = editor.getMarkupModel();
        for (FindResult result : results) {
          int startOffset = result.getStartOffset();
          int endOffset = result.getEndOffset();
          if (startOffset == start && endOffset == end) continue;
          highlighters.add(markupModel.addRangeHighlighter(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES, startOffset, endOffset,
                                                           HighlightManagerImpl.OCCURRENCE_LAYER, HighlighterTargetArea.EXACT_RANGE));
        }
        editor.putUserData(SELECTION_HIGHLIGHTS, highlighters);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
    return true;
  }

  private static void removeSelectionHighlights(@NotNull Editor editor) {
    MarkupModel markupModel = editor.getMarkupModel();
    Collection<RangeHighlighter> oldHighlighters = editor.getUserData(SELECTION_HIGHLIGHTS);
    if (oldHighlighters != null) {
      editor.putUserData(SELECTION_HIGHLIGHTS, null);
      for (RangeHighlighter highlighter : oldHighlighters) {
        markupModel.removeHighlighter(highlighter);
      }
    }
    editor.putUserData(HIGHLIGHTED_TEXT, null);
  }

  private static void updateHighlighted(@NotNull Project project, @NotNull Editor editor, @NotNull Alarm alarm) {
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
        BraceHighlightingHandler handler = new BraceHighlightingHandler(project, newEditor, alarm, newFile);
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

  private static void clearBraces(@NotNull Project project, @NotNull Editor editor, @NotNull Alarm alarm) {
    BackgroundHighlightingUtil.lookForInjectedFileInOtherThread(project, editor, (__, ___) -> null, (foundFile, newEditor, __) -> {
      BraceHighlightingHandler handler = new BraceHighlightingHandler(project, newEditor, alarm, foundFile);
      handler.clearBraceHighlighters();
    });
  }

  private static void submitIdentifierHighlighterPass(@NotNull Editor hostEditor,
                                                      int offsetBefore,
                                                      @NotNull PsiFile newFile,
                                                      @NotNull Editor newEditor) {
    ReadAction.nonBlocking(() -> {
        if (!newFile.isValid()) {
          return null;
        }
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
          HighlightingSessionImpl.runInsideHighlightingSession(hostPsiFile, hostEditor.getColorsScheme(),
                                                               ProperTextRange.create(hostPsiFile.getTextRange()), false, session -> {
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
    //noinspection UsagesOfObsoleteApi
    return Objects.requireNonNull(StartupActivity.Companion.getPOST_STARTUP_ACTIVITY()
                                    .findExtension(BackgroundHighlighterProjectActivity.class)).impl.alarm;
  }

  @TestOnly
  static void enableListenersInTest(@NotNull Project project, @NotNull Disposable disposable) {
    registerListeners(project, disposable, getAlarm());
  }

  private static final class HighlightIdentifiersKey {
  }

  private static final class HighlightSelectionKey {}
}