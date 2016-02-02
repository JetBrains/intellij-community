/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AutoPopupController implements Disposable {
  /**
   * Settings this user data key to the editor with a completion provider
   * makes the autopopup scheduling ignore the state of the corresponding setting.
   * <p/>
   * This doesn't affect other conditions when autopopup is not possible (e.g. power save mode).
   */
  public static final Key<Boolean> ALWAYS_AUTO_POPUP = Key.create("Always Show Completion Auto-Popup");

  private final Project myProject;
  private final Alarm myAlarm = new Alarm();

  public static AutoPopupController getInstance(Project project){
    return ServiceManager.getService(project, AutoPopupController.class);
  }

  public AutoPopupController(Project project) {
    myProject = project;
    setupListeners();
  }

  private void setupListeners() {
    ActionManagerEx.getInstanceEx().addAnActionListener(new AnActionListener() {
      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        cancelAllRequest();
      }

      @Override
      public void beforeEditorTyping(char c, DataContext dataContext) {
        cancelAllRequest();
      }


      @Override
      public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
      }
    }, this);

    IdeEventQueue.getInstance().addActivityListener(new Runnable() {
      @Override
      public void run() {
        cancelAllRequest();
      }
    }, this);
  }

  public void autoPopupMemberLookup(final Editor editor, @Nullable final Condition<PsiFile> condition){
    scheduleAutoPopup(editor, condition);
  }

  public void scheduleAutoPopup(final Editor editor, @Nullable final Condition<PsiFile> condition) {
    if (ApplicationManager.getApplication().isUnitTestMode() && !CompletionAutoPopupHandler.ourTestingAutopopup) {
      return;
    }

    boolean alwaysAutoPopup = editor != null && Boolean.TRUE.equals(editor.getUserData(ALWAYS_AUTO_POPUP));
    if (!CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP && !alwaysAutoPopup) {
      return;
    }
    if (PowerSaveMode.isEnabled()) {
      return;
    }

    if (!CompletionServiceImpl.isPhase(CompletionPhase.CommittingDocuments.class, CompletionPhase.NoCompletion.getClass())) {
      return;
    }

    final CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (currentCompletion != null) {
      currentCompletion.closeAndFinish(true);
    }

    final CompletionPhase.CommittingDocuments phase = new CompletionPhase.CommittingDocuments(null, editor);
    CompletionServiceImpl.setCompletionPhase(phase);
    phase.ignoreCurrentDocumentChange();

    CompletionAutoPopupHandler.runLaterWithCommitted(myProject, editor.getDocument(), new Runnable() {
      @Override
      public void run() {
        if (phase.checkExpired()) return;

        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
        if (file != null && condition != null && !condition.value(file)) {
          CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
          return;
        }

        CompletionAutoPopupHandler.invokeCompletion(CompletionType.BASIC, true, myProject, editor, 0, false);
      }
    });
  }

  public void scheduleAutoPopup(final Editor editor) {
    scheduleAutoPopup(editor, null);
  }

  private void addRequest(final Runnable request, final int delay) {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        myAlarm.addRequest(request, delay);
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }

  private void cancelAllRequest() {
    myAlarm.cancelAllRequests();
  }

  public void autoPopupParameterInfo(@NotNull final Editor editor, @Nullable final PsiElement highlightedMethod){
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (DumbService.isDumb(myProject)) return;
    if (PowerSaveMode.isEnabled()) return;

    ApplicationManager.getApplication().assertIsDispatchThread();
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_PARAMETER_INFO) {
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      PsiFile file = documentManager.getPsiFile(editor.getDocument());
      if (file == null) return;

      if (!documentManager.isUncommited(editor.getDocument())) {
        file = documentManager.getPsiFile(InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file).getDocument());
        if (file == null) return;
      }

      final PsiFile file1 = file;
      final Runnable request = new Runnable(){
        @Override
        public void run(){
          if (myProject.isDisposed() || DumbService.isDumb(myProject)) return;
          documentManager.commitAllDocuments();
          if (editor.isDisposed() || !editor.getComponent().isShowing()) return;
          int lbraceOffset = editor.getCaretModel().getOffset() - 1;
          try {
            ShowParameterInfoHandler.invoke(myProject, editor, file1, lbraceOffset, highlightedMethod);
          }
          catch (IndexNotReadyException ignored) { //anything can happen on alarm
          }
        }
      };

      addRequest(request, settings.PARAMETER_INFO_DELAY);
    }
  }

  @Override
  public void dispose() {
  }
}
