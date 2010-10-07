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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;

/**
 * @author peter
 */
public class CompletionAutoPopupHandler extends TypedHandlerDelegate {
  private Editor myEditor;

  //todo store this in some less nasty place
  public static int ourLastOffset;

  private static final Key<Object> COMPLETION_AUTO_POPUP_HANDLER = Key.create("CompletionAutoPopupHandler");

  @Override
  public Result checkAutoPopup(char charTyped, final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) return Result.CONTINUE;

    if (!Character.isLetter(charTyped) && charTyped != '_') {
      finishAutopopupCompletion();
      return Result.CONTINUE;
    }

    if (myEditor != null) {
      if (editor != myEditor || editor.getCaretModel().getOffset() != ourLastOffset + 1) {
        finishAutopopupCompletion();
      } else {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourLastOffset = editor.getCaretModel().getOffset();
      }
      return Result.CONTINUE;
    } else {
      if (LookupManager.getActiveLookup(editor) != null) {
        return Result.CONTINUE;
      }
    }

    myEditor = editor;
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourLastOffset = editor.getCaretModel().getOffset();

    trackProjectClosing(project);
    trackUserActivity(project, editor);

    final boolean isMainEditor = FileEditorManager.getInstance(project).getSelectedTextEditor() == editor;

    AutoPopupController.getInstance(project).invokeAutoPopupRunnable(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed() || !file.isValid()) return;
        if (editor.isDisposed() || isMainEditor && FileEditorManager.getInstance(project).getSelectedTextEditor() != editor) return;

        new CodeCompletionHandlerBase(CompletionType.BASIC, false, false).invoke(project, editor);
        final Lookup lookup = LookupManager.getActiveLookup(editor);
        if (lookup != null) {
          lookup.addLookupListener(new LookupAdapter() {
            @Override
            public void itemSelected(LookupEvent event) {
              myEditor = null;
            }

            @Override
            public void lookupCanceled(LookupEvent event) {
              if (event.isCanceledExplicitly()) {
                myEditor = null;
              }
            }
          });
        }
      }
    }, CodeInsightSettings.getInstance().AUTO_LOOKUP_DELAY);
    return Result.STOP;
  }

  private void trackUserActivity(Project project, final Editor editor) {
    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void selectionChanged(FileEditorManagerEvent event) {
        finishAutopopupCompletion();
        connection.disconnect();
      }
    });

    editor.addEditorMouseListener(new EditorMouseAdapter() {
      @Override
      public void mouseClicked(EditorMouseEvent e) {
        editor.removeEditorMouseListener(this);
        finishAutopopupCompletion();
      }
    });

    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (StringUtil.containsLineBreak(e.getNewFragment())) {
          finishAutopopupCompletion();
          editor.getDocument().removeDocumentListener(this);
        }
      }
    });
  }

  private void trackProjectClosing(Project project) {
    if (project.getUserData(COMPLETION_AUTO_POPUP_HANDLER) == null) {
      project.putUserData(COMPLETION_AUTO_POPUP_HANDLER, new Object());
      Disposer.register(project, new Disposable() {
        @Override
        public void dispose() {
          myEditor = null;
        }
      });
    }
  }

  private void finishAutopopupCompletion() {
    myEditor = null;
    final CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (currentCompletion != null) {
      currentCompletion.closeAndFinish();
    }
  }
}
