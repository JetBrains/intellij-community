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
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public class CompletionAutoPopupHandler extends TypedHandlerDelegate {
  public static volatile boolean ourTestingAutopopup = false;

  @Override
  public Result beforeCharTyped(char c,
                                Project project,
                                Editor editor,
                                PsiFile file,
                                FileType fileType) {
    CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    if (phase instanceof CompletionPhase.EmptyAutoPopup) {
      ((CompletionPhase.EmptyAutoPopup)phase).handleTyping(c);
      return Result.STOP;
    }

    return Result.CONTINUE;
  }

  @Override
  public Result checkAutoPopup(char charTyped, final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) return Result.CONTINUE;

    if (LookupManager.getActiveLookup(editor) != null) {
      return Result.CONTINUE;
    }

    if (!Character.isLetter(charTyped) && charTyped != '_') {
      if (CompletionServiceImpl.isPhase(CompletionPhase.EmptyAutoPopup.class)) {
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      }
      return Result.CONTINUE;
    }

    if (!CompletionServiceImpl.isPhase(CompletionPhase.AutoPopupAlarm.class, CompletionPhase.NoCompletion.getClass())) {
      if ("peter".equals(System.getProperty("user.name")) && ApplicationManagerEx.getApplicationEx().isInternal()) {
        System.out.println(CompletionServiceImpl.getCompletionPhase() + " at " + System.currentTimeMillis());
      }
      return Result.CONTINUE;
    }

    final boolean isMainEditor = FileEditorManager.getInstance(project).getSelectedTextEditor() == editor;

    final CompletionPhase.AutoPopupAlarm phase = new CompletionPhase.AutoPopupAlarm();
    CompletionServiceImpl.setCompletionPhase(phase);

    final Runnable request = new Runnable() {
      @Override
      public void run() {
        if (CompletionServiceImpl.getCompletionPhase() != phase) return;

        if (project.isDisposed() || !file.isValid()) return;
        if (editor.isDisposed() || isMainEditor && FileEditorManager.getInstance(project).getSelectedTextEditor() != editor) return;
        if (ApplicationManager.getApplication().isWriteAccessAllowed()) return; //it will fail anyway
        if (DumbService.getInstance(project).isDumb()) return;

        invokeAutoPopupCompletion(project, editor);
      }
    };
    AutoPopupController.getInstance(project).invokeAutoPopupRunnable(request, CodeInsightSettings.getInstance().AUTO_LOOKUP_DELAY);
    return Result.STOP;
  }

  public static void invokeAutoPopupCompletion(Project project, final Editor editor) {
    new CodeCompletionHandlerBase(CompletionType.BASIC, false, true).invokeCompletion(project, editor, 0, false);
  }

}
