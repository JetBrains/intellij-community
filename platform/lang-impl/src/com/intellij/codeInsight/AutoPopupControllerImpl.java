// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static com.intellij.codeInsight.completion.CompletionPhase.*;

public class AutoPopupControllerImpl extends AutoPopupController {
  private final Project myProject;
  private final Alarm myAlarm;

  public AutoPopupControllerImpl(@NotNull Project project) {
    myProject = project;

    myAlarm = new Alarm(myProject);
    setupListeners();
  }

  private void setupListeners() {
    ApplicationManager.getApplication().getMessageBus().connect(myProject).subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
        cancelAllRequests();
      }

      @Override
      public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
        cancelAllRequests();
      }
    });

    IdeEventQueue.getInstance().addActivityListener(this::cancelAllRequests, myProject);
  }

  @Override
  public void autoPopupMemberLookup(final Editor editor, final @Nullable Condition<? super PsiFile> condition){
    autoPopupMemberLookup(editor, CompletionType.BASIC, condition);
  }

  @Override
  public void autoPopupMemberLookup(final Editor editor, CompletionType completionType, final @Nullable Condition<? super PsiFile> condition){
    scheduleAutoPopup(editor, completionType, condition);
  }

  @Override
  public void scheduleAutoPopup(@NotNull Editor editor, @NotNull CompletionType completionType, final @Nullable Condition<? super PsiFile> condition) {
    if (ApplicationManager.getApplication().isUnitTestMode() && !TestModeFlags.is(CompletionAutoPopupHandler.ourTestingAutopopup)) {
      return;
    }

    boolean alwaysAutoPopup = Boolean.TRUE.equals(editor.getUserData(ALWAYS_AUTO_POPUP));
    if (!CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP && !alwaysAutoPopup) {
      return;
    }
    if (PowerSaveMode.isEnabled()) {
      return;
    }

    if (!CompletionServiceImpl.isPhase(CommittingDocuments.class, NoCompletion.getClass(), EmptyAutoPopup.class)) {
      return;
    }

    final CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCurrentCompletionProgressIndicator();
    if (currentCompletion != null) {
      currentCompletion.closeAndFinish(true);
    }

    CommittingDocuments.scheduleAsyncCompletion(editor, completionType, condition, myProject, null);
  }

  @Override
  public void scheduleAutoPopup(final Editor editor) {
    scheduleAutoPopup(editor, CompletionType.BASIC, null);
  }

  @Override
  public void cancelAllRequests() {
    myAlarm.cancelAllRequests();
  }

  @Override
  public void autoPopupParameterInfo(final @NotNull Editor editor, final @Nullable PsiElement highlightedMethod) {
    if (PowerSaveMode.isEnabled()) return;

    ThreadingAssertions.assertEventDispatchThread();
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_PARAMETER_INFO) {
      AtomicInteger offset = new AtomicInteger(-1);
      ReadAction.nonBlocking(() -> {
          offset.set(editor.getCaretModel().getOffset());
          final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
          PsiFile file = documentManager.getPsiFile(editor.getDocument());
          if (file == null) return;

          if (!documentManager.isUncommited(editor.getDocument())) {
            file = documentManager.getPsiFile(InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file).getDocument());
            if (file == null) return;
          }

          Runnable request = () -> {
            if (!myProject.isDisposed() && !editor.isDisposed() && UIUtil.isShowing(editor.getContentComponent())) {
              int lbraceOffset = offset.get() - 1;
              try {
                PsiFile file1 = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
                if (file1 != null) {
                  ShowParameterInfoHandler.invoke(myProject, editor, file1, lbraceOffset, highlightedMethod, false,
                                                  true, null);
                }
              }
              catch (IndexNotReadyException ignored) { //anything can happen on alarm
              }
            }
          };

          myAlarm.addRequest(() -> documentManager.performLaterWhenAllCommitted(request), settings.PARAMETER_INFO_DELAY);
        }).expireWith(myAlarm)
        .coalesceBy(this, editor)
        .expireWhen(() -> {
          int initialOffset = offset.get();
          return editor.isDisposed() || initialOffset != -1 && editor.getCaretModel().getOffset() != initialOffset;
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  @Override
  @TestOnly
  public void waitForDelayedActions(long timeout, @NotNull TimeUnit unit) throws TimeoutException {
    long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
    while (System.currentTimeMillis() < deadline) {
      UIUtil.dispatchAllInvocationEvents();
      if (myAlarm.isEmpty()) return;
      LockSupport.parkNanos(10_000_000);
    }
    throw new TimeoutException();
  }
}
