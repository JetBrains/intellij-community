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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author peter
 */
public class CompletionAutoPopupHandler extends TypedHandlerDelegate {
  public static boolean ourTestingAutopopup = false;
  private boolean myAutopopupShown;
  private boolean myGuard;

  @Override
  public Result beforeCharTyped(char c,
                                Project project,
                                Editor editor,
                                PsiFile file,
                                FileType fileType) {
    if (myAutopopupShown && LookupManager.getActiveLookup(editor) == null) {
      myGuard = true;
      try {
        EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, String.valueOf(c), true);
      }
      finally {
        myGuard = false;
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

    if (myAutopopupShown || LookupManager.getActiveLookup(editor) != null) {
      return Result.CONTINUE;
    }

    final boolean isMainEditor = FileEditorManager.getInstance(project).getSelectedTextEditor() == editor;

    final Runnable request = new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed() || !file.isValid()) return;
        if (editor.isDisposed() || isMainEditor && FileEditorManager.getInstance(project).getSelectedTextEditor() != editor) return;

        new CodeCompletionHandlerBase(CompletionType.BASIC, false, false).invoke(project, editor);

        myAutopopupShown = true;
        trackUserActivity(project, editor);

        final Lookup lookup = LookupManager.getActiveLookup(editor);
        if (lookup != null) {
          lookup.addLookupListener(new LookupAdapter() {
            @Override
            public void itemSelected(LookupEvent event) {
              myAutopopupShown = false;
            }

            @Override
            public void lookupCanceled(LookupEvent event) {
              if (event.isCanceledExplicitly()) {
                myAutopopupShown = false;
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

  private void trackUserActivity(Project project, final Editor editor) {
    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void selectionChanged(FileEditorManagerEvent event) {
        if (finishAutopopupCompletion(editor, false)) {
          connection.disconnect();
        }
      }
    });

    editor.addEditorMouseListener(new EditorMouseAdapter() {
      @Override
      public void mouseClicked(EditorMouseEvent e) {
        if (finishAutopopupCompletion(editor, false)) {
          editor.removeEditorMouseListener(this);
        }
      }
    });

    editor.getCaretModel().addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        if (finishAutopopupCompletion(editor, false)) {
          editor.getCaretModel().removeCaretListener(this);
        }
      }
    });

    editor.getSelectionModel().addSelectionListener(new SelectionListener() {
      @Override
      public void selectionChanged(SelectionEvent e) {
        if (finishAutopopupCompletion(editor, false)) {
          editor.getSelectionModel().removeSelectionListener(this);
        }
      }
    });

    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (finishAutopopupCompletion(editor, false)) {
          editor.getDocument().removeDocumentListener(this);
        }
      }
    });

    final LookupManager lookupManager = LookupManager.getInstance(project);
    lookupManager.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getNewValue() != null && finishAutopopupCompletion(editor, true)) {
          lookupManager.removePropertyChangeListener(this);
        }
      }
    });
  }

  private boolean finishAutopopupCompletion(Editor editor, boolean neglectLookup) {
    if (!myAutopopupShown) {
      return true; //to disconnect all the listeners
    }

    if (myGuard) {
      return false;
    }

    if (!neglectLookup && LookupManager.getActiveLookup(editor) != null) { //the events during visible lookup period are handled separately
      return false;
    }

    myAutopopupShown = false;
    final CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (currentCompletion != null) {
      currentCompletion.closeAndFinish(true);
    }
    return true;
  }
}
