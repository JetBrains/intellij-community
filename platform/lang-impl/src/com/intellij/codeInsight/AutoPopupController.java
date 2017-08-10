/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionId;
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
  /**
   * If editor has Boolean.TRUE by this key completion popup would be shown without advertising text at the bottom.
   */
  public static final Key<Boolean> NO_ADS = Key.create("Show Completion Auto-Popup without Ads");

  /**
   * If editor has Boolean.TRUE by this key completion popup would be shown every time when editor gets focus.
   * For example this key can be used for TextFieldWithAutoCompletion.
   * (TextFieldWithAutoCompletion looks like standard JTextField and completion shortcut is not obvious to be active)
   */
  public static final Key<Boolean> AUTO_POPUP_ON_FOCUS_GAINED = Key.create("Show Completion Auto-Popup On Focus Gained");


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

    IdeEventQueue.getInstance().addActivityListener(() -> cancelAllRequest(), this);
  }

  public void autoPopupMemberLookup(final Editor editor, @Nullable final Condition<PsiFile> condition){
    autoPopupMemberLookup(editor, CompletionType.BASIC, condition);
  }

  public void autoPopupMemberLookup(final Editor editor, CompletionType completionType, @Nullable final Condition<PsiFile> condition){
    scheduleAutoPopup(editor, completionType, condition);
  }

  public void scheduleAutoPopup(final Editor editor, CompletionType completionType, @Nullable final Condition<PsiFile> condition) {
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

    runTransactionWithEverythingCommitted(myProject, () -> {
      if (phase.checkExpired()) return;

      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (file != null && condition != null && !condition.value(file)) {
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
        return;
      }

      CompletionAutoPopupHandler.invokeCompletion(completionType, true, myProject, editor, 0, false);
    });
  }

  public void scheduleAutoPopup(final Editor editor) {
    scheduleAutoPopup(editor, CompletionType.BASIC, null);
  }

  private void addRequest(final Runnable request, final int delay) {
    Runnable runnable = () -> myAlarm.addRequest(request, delay);
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

      Runnable request = () -> {
        if (!myProject.isDisposed() && !DumbService.isDumb(myProject) && !editor.isDisposed() && editor.getComponent().isShowing()) {
          int lbraceOffset = editor.getCaretModel().getOffset() - 1;
          try {
            PsiFile file1 = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
            ShowParameterInfoHandler.invoke(myProject, editor, file1, lbraceOffset, highlightedMethod, false, true);
          }
          catch (IndexNotReadyException ignored) { //anything can happen on alarm
          }
        }
      };

      addRequest(() -> documentManager.performLaterWhenAllCommitted(request), settings.PARAMETER_INFO_DELAY);
    }
  }

  @Override
  public void dispose() {
  }

  public static void runTransactionWithEverythingCommitted(@NotNull final Project project, @NotNull final Runnable runnable) {
    TransactionGuard guard = TransactionGuard.getInstance();
    TransactionId id = guard.getContextTransaction();
    final PsiDocumentManager pdm = PsiDocumentManager.getInstance(project);
    pdm.performLaterWhenAllCommitted(() -> guard.submitTransaction(project, id, () -> {
      if (pdm.hasUncommitedDocuments()) {
        // no luck, will try later
        runTransactionWithEverythingCommitted(project, runnable);
      }
      else {
        runnable.run();
      }
    }));
  }
}
