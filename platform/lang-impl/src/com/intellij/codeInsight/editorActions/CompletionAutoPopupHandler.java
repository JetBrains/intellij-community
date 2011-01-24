/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupAdapter;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author peter
 */
public class CompletionAutoPopupHandler extends TypedHandlerDelegate {
  private static final Key<AutoPopupState> STATE_KEY = Key.create("AutopopupSTATE_KEY");
  public static volatile boolean ourTestingAutopopup = false;

  @Override
  public Result beforeCharTyped(char c,
                                Project project,
                                Editor editor,
                                PsiFile file,
                                FileType fileType) {
    final AutoPopupState state = getAutoPopupState(editor);
    if (state != null && LookupManager.getActiveLookup(editor) == null) {
      state.changeGuard = true;
      try {
        EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, String.valueOf(c), true);
      }
      finally {
        state.changeGuard = false;
      }
      return Result.STOP;
    }

    return Result.CONTINUE;
  }

  @Override
  public Result checkAutoPopup(char charTyped, final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) return Result.CONTINUE;

    if (!Character.isLetter(charTyped) && charTyped != '_') {
      finishAutopopupCompletion(editor, false);
      return Result.CONTINUE;
    }

    if (getAutoPopupState(editor) != null || LookupManager.getActiveLookup(editor) != null) {
      return Result.CONTINUE;
    }

    final CharSequence text = editor.getDocument().getCharsSequence();
    final int offset = editor.getSelectionModel().hasSelection() ? editor.getSelectionModel().getSelectionEnd() : editor.getCaretModel().getOffset();
    if (text.length() > offset && Character.isUnicodeIdentifierPart(text.charAt(offset))) {
      return Result.CONTINUE;
    }

    final boolean isMainEditor = FileEditorManager.getInstance(project).getSelectedTextEditor() == editor;

    CompletionServiceImpl.setCompletionPhase(CompletionPhase.autoPopupAlarm);

    final Runnable request = new Runnable() {
      @Override
      public void run() {
        if (CompletionServiceImpl.getCompletionPhase() != CompletionPhase.autoPopupAlarm) return;

        if (project.isDisposed() || !file.isValid()) return;
        if (editor.isDisposed() || isMainEditor && FileEditorManager.getInstance(project).getSelectedTextEditor() != editor) return;
        if (ApplicationManager.getApplication().isWriteAccessAllowed()) return; //it will fail anyway
        if (DumbService.getInstance(project).isDumb()) return;

        new CodeCompletionHandlerBase(CompletionType.BASIC, false, true).invoke(project, editor);

        final AutoPopupState state = new AutoPopupState(project, editor);
        editor.putUserData(STATE_KEY, state);

        final Lookup lookup = LookupManager.getActiveLookup(editor);
        if (lookup != null) {
          lookup.addLookupListener(new LookupAdapter() {
            @Override
            public void itemSelected(LookupEvent event) {
              final AutoPopupState state = getAutoPopupState(editor);
              if (state != null) {
                state.stopAutoPopup();
              }
            }

            @Override
            public void lookupCanceled(LookupEvent event) {
              final AutoPopupState state = getAutoPopupState(editor);
              if (event.isCanceledExplicitly() && state != null) {
                state.stopAutoPopup();
              }
            }
          });
        }
      }
    };
    if (ourTestingAutopopup) {
      ApplicationManager.getApplication().invokeLater(request);
    } else {
      AutoPopupController.getInstance(project).invokeAutoPopupRunnable(request, CodeInsightSettings.getInstance().AUTO_LOOKUP_DELAY);
    }
    return Result.STOP;
  }

  @Nullable
  private static AutoPopupState getAutoPopupState(Editor editor) {
    return editor.getUserData(STATE_KEY);
  }

  private static void finishAutopopupCompletion(Editor editor, boolean neglectLookup) {
    final AutoPopupState state = getAutoPopupState(editor);
    if (state == null || state.changeGuard) {
      return;
    }

    if (!neglectLookup && LookupManager.getActiveLookup(editor) != null) { //the events during visible lookup period are handled separately
      return;
    }

    final CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (CompletionServiceImpl.isPhase(CompletionPhase.autoPopupAlarm, CompletionPhase.emptyAutoPopup, CompletionPhase.possiblyDisturbingAutoPopup)) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.noCompletion);
      assert currentCompletion == null;
    } else {
      if (currentCompletion != null) {
        currentCompletion.closeAndFinish(true);
      }
      CompletionServiceImpl.assertPhase(CompletionPhase.noCompletion);
    }

    state.stopAutoPopup();
  }


  private static class AutoPopupState {
    final Editor editor;
    final Project project;
    final MessageBusConnection connection;
    final EditorMouseAdapter mouseListener;
    final CaretListener caretListener;
    final DocumentAdapter documentListener;
    final PropertyChangeListener lookupListener;
    boolean changeGuard = false;

    private AutoPopupState(final Project project, final Editor editor) {
      this.editor = editor;
      this.project = project;
      connection = project.getMessageBus().connect();
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
        @Override
        public void selectionChanged(FileEditorManagerEvent event) {
          finishAutopopupCompletion(editor, false);
        }
      });

      mouseListener = new EditorMouseAdapter() {
        @Override
        public void mouseClicked(EditorMouseEvent e) {
          finishAutopopupCompletion(editor, false);
        }
      };

      caretListener = new CaretListener() {
        @Override
        public void caretPositionChanged(CaretEvent e) {
          finishAutopopupCompletion(editor, false);
        }
      };
      editor.getSelectionModel().addSelectionListener(new SelectionListener() {
        @Override
        public void selectionChanged(SelectionEvent e) {
          finishAutopopupCompletion(editor, false);
        }
      });
      documentListener = new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          finishAutopopupCompletion(editor, false);
        }
      };
      lookupListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (evt.getNewValue() != null) {
            finishAutopopupCompletion(editor, true);
          }
        }
      };

      editor.addEditorMouseListener(mouseListener);
      editor.getCaretModel().addCaretListener(caretListener);
      editor.getDocument().addDocumentListener(documentListener);
      LookupManager.getInstance(project).addPropertyChangeListener(lookupListener);
    }

    void stopAutoPopup() {
      connection.disconnect();
      editor.removeEditorMouseListener(mouseListener);
      editor.getCaretModel().removeCaretListener(caretListener);
      editor.getDocument().removeDocumentListener(documentListener);
      LookupManager.getInstance(project).removePropertyChangeListener(lookupListener);

      editor.putUserData(STATE_KEY, null);
    }

  }
}
