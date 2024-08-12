// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeWithMe.ClientId;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public abstract class CompletionPhase implements Disposable {
  public static final Key<TypedEvent> AUTO_POPUP_TYPED_EVENT = Key.create("AutoPopupTypedEvent");

  private static final Logger LOG = Logger.getInstance(CompletionPhase.class);

  public static final CompletionPhase NoCompletion = new CompletionPhase(null) {
    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      return time;
    }

    @Override
    public String toString() {
      return "NoCompletion";
    }
  };

  public final CompletionProgressIndicator indicator;

  protected CompletionPhase(@Nullable CompletionProgressIndicator indicator) {
    this.indicator = indicator;
  }

  @Override
  public void dispose() {
  }

  public abstract int newCompletionStarted(int time, boolean repeated);

  public static final class CommittingDocuments extends CompletionPhase {
    private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Completion Preparation", 1);
    boolean replaced;
    private final ActionTracker myTracker;
    private final @Nullable TypedEvent myEvent;
    private int myRequestCount = 1;

    CommittingDocuments(@Nullable CompletionProgressIndicator prevIndicator, @NotNull Editor editor,
                        @Nullable TypedEvent event) {
      super(prevIndicator);
      myTracker = new ActionTracker(editor, this);
      myEvent = event;
    }

    public void ignoreCurrentDocumentChange() {
      myTracker.ignoreCurrentDocumentChange();
    }

    private boolean isExpired() {
      return myTracker.hasAnythingHappened() || myRequestCount <= 0;
    }

    private @Nullable TypedEvent getEvent() {
      return myEvent;
    }

    void incrementRequestCount() {
      myRequestCount++;
      LOG.trace("Increment request count :: new myRequestCount=" + myRequestCount);
    }

    private void decrementRequestCount() {
      myRequestCount--;
      LOG.trace("Decrement request count :: new myRequestCount=" + myRequestCount);
    }

    private void requestCompleted() {
      LOG.trace("Request completed");
      myRequestCount = 0;
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      return time;
    }

    @Override
    public void dispose() {
      LOG.trace("Dispose completion phase: " + this);
      myRequestCount = 0;
      if (!replaced && indicator != null) {
        indicator.closeAndFinish(true);
      }
    }

    @Override
    public String toString() {
      return "CommittingDocuments{hasIndicator=" + (indicator != null) + '}';
    }

    @ApiStatus.Internal
    public static void scheduleAsyncCompletion(@NotNull Editor _editor,
                                               @NotNull CompletionType completionType,
                                               @Nullable Condition<? super PsiFile> condition,
                                               @NotNull Project project,
                                               @Nullable CompletionProgressIndicator prevIndicator) {
      LOG.trace("Schedule async completion");
      Editor topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(_editor);
      int offset = topLevelEditor.getCaretModel().getOffset();

      CommittingDocuments phase = getCompletionPhase(prevIndicator, topLevelEditor, _editor.getUserData(AUTO_POPUP_TYPED_EVENT));

      boolean autopopup = prevIndicator == null || prevIndicator.isAutopopupCompletion();

      ReadAction
        .nonBlocking(() -> {
          if (phase.isExpired()) {
            LOG.trace("Phase is expired");
            return null;
          }

          LOG.trace("Start non-blocking read action :: phase=" + phase.replaced);
          // retrieve the injected file from scratch since our typing might have destroyed the old one completely
          PsiFile topLevelFile = PsiDocumentManager.getInstance(project).getPsiFile(topLevelEditor.getDocument());
          Editor completionEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(topLevelEditor, topLevelFile, offset);
          PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(completionEditor.getDocument());
          if (file == null ||
              autopopup && shouldSkipAutoPopup(completionEditor, file) ||
              condition != null && !condition.value(file)) {
            LOG.trace("File is null or should skip auto popup or condition is not met :: file=" + file + ", condition=" + condition);
            return null;
          }

          loadContributorsOutsideEdt(completionEditor, file);

          return completionEditor;
        })
        .withDocumentsCommitted(project)
        .expireWith(phase)
        .finishOnUiThread(ModalityState.current(), completionEditor -> {
          LOG.trace("Finish on UI thread :: completionEditor=" + completionEditor);
          if (completionEditor != null && !phase.isExpired()) {
            LOG.trace("Starting completion phase :: completionEditor=" + completionEditor);
            phase.requestCompleted();
            int time = prevIndicator == null ? 0 : prevIndicator.getInvocationCount();
            CodeCompletionHandlerBase handler = CodeCompletionHandlerBase.createHandler(completionType, false, autopopup, false);
            handler.invokeCompletion(project, completionEditor, time, false);
          }
          else if (phase == CompletionServiceImpl.getCompletionPhase()) {
            LOG.trace("Setting NoCompletion phase :: completionEditor=" + completionEditor + ", isExpired=" + phase.isExpired());
            phase.decrementRequestCount();
            if (phase.isExpired()) {
              CompletionServiceImpl.setCompletionPhase(NoCompletion);
            }
          }
        })
        .submit(ourExecutor);
    }

    private static @NotNull CommittingDocuments getCompletionPhase(@Nullable CompletionProgressIndicator prevIndicator,
                                                                   Editor topLevelEditor,
                                                                   @Nullable TypedEvent event) {
      if (event != null) {
        CompletionPhase currentPhase = CompletionServiceImpl.getCompletionPhase();
        if (currentPhase instanceof CommittingDocuments committingPhase &&
            !committingPhase.isExpired() &&
            event.equals(committingPhase.getEvent())) {
          committingPhase.incrementRequestCount();
          return committingPhase;
        }
      }
      CommittingDocuments phase = new CommittingDocuments(prevIndicator, topLevelEditor, event);
      CompletionServiceImpl.setCompletionPhase(phase);
      phase.ignoreCurrentDocumentChange();
      return phase;
    }

    @ApiStatus.Internal
    public static void loadContributorsOutsideEdt(Editor editor, PsiFile file) {
      CompletionContributor.forLanguage(PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset()));
    }

    @ApiStatus.Internal
    public static boolean shouldSkipAutoPopup(Editor editor, PsiFile psiFile) {
      int offset = editor.getCaretModel().getOffset();
      int psiOffset = Math.max(0, offset - 1);

      PsiElement elementAt = psiFile.findElementAt(psiOffset);
      if (elementAt == null) return true;

      Language language = PsiUtilCore.findLanguageFromElement(elementAt);

      for (CompletionConfidence confidence : CompletionConfidenceEP.forLanguage(language)) {
        try {
          ThreeState result = confidence.shouldSkipAutopopup(elementAt, psiFile, offset);
          if (result != ThreeState.UNSURE) {
            LOG.debug(confidence + " has returned shouldSkipAutopopup=" + result);
            return result == ThreeState.YES;
          }
        }
        catch (IndexNotReadyException e) {
          LOG.debug(e);
          return true;
        }
      }
      return false;
    }
  }

  public static final class Synchronous extends CompletionPhase {
    public Synchronous(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      CompletionServiceImpl.assertPhase(NoCompletion.getClass()); // will fail and log valuable info
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
      return time;
    }
  }

  public static final class BgCalculation extends CompletionPhase {
    boolean modifiersChanged = false;
    private final @NotNull ClientId ownerId = ClientId.getCurrent();

    public BgCalculation(final CompletionProgressIndicator indicator) {
      super(indicator);
      ApplicationManager.getApplication().addApplicationListener(new ApplicationListener() {
        @Override
        public void beforeWriteActionStart(@NotNull Object action) {
          if (!indicator.getLookup().isLookupDisposed() && !indicator.isCanceled() && ownerId.equals(ClientId.getCurrent())) {
            indicator.scheduleRestart();
          }
        }
      }, this);
      if (indicator.isAutopopupCompletion()) {
        // lookup is not visible, we have to check ourselves if editor retains focus
        ((EditorEx)indicator.getEditor()).addFocusListener(new FocusChangeListener() {
          @Override
          public void focusLost(@NotNull Editor editor, @NotNull FocusEvent event) {
            // When ScreenReader is active the lookup gets focus on show and we should not close it.
            if (ScreenReader.isActive() &&
                event.getOppositeComponent() != null &&
                indicator.getLookup().getComponent() != null &&
                // Check the opposite is in the lookup ancestor
                SwingUtilities.getWindowAncestor(event.getOppositeComponent()) ==
                SwingUtilities.getWindowAncestor(indicator.getLookup().getComponent())) {
              return;
            }
            indicator.closeAndFinish(true);
          }
        }, this);
      }
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      indicator.closeAndFinish(false);
      return indicator.nextInvocationCount(time, repeated);
    }
  }

  public static final class ItemsCalculated extends CompletionPhase {

    public ItemsCalculated(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      indicator.closeAndFinish(false);
      return indicator.nextInvocationCount(time, repeated);
    }
  }

  public abstract static class ZombiePhase extends CompletionPhase {

    ZombiePhase(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    void expireOnAnyEditorChange(@NotNull Editor editor) {
      editor.getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      }, this);
      editor.getSelectionModel().addSelectionListener(new SelectionListener() {
        @Override
        public void selectionChanged(@NotNull SelectionEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      }, this);
      editor.getCaretModel().addCaretListener(new CaretListener() {
        @Override
        public void caretPositionChanged(@NotNull CaretEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      }, this);
    }
  }

  public static final class InsertedSingleItem extends ZombiePhase {
    public final Runnable restorePrefix;

    InsertedSingleItem(CompletionProgressIndicator indicator, Runnable restorePrefix) {
      super(indicator);
      this.restorePrefix = restorePrefix;
      expireOnAnyEditorChange(indicator.getEditor());
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
      if (repeated) {
        indicator.restorePrefix(restorePrefix);
      }
      return indicator.nextInvocationCount(time, repeated);
    }
  }

  public static final class NoSuggestionsHint extends ZombiePhase {
    NoSuggestionsHint(@Nullable LightweightHint hint, CompletionProgressIndicator indicator) {
      super(indicator);
      expireOnAnyEditorChange(indicator.getEditor());
      if (hint != null) {
        HintListener hintListener = event -> CompletionServiceImpl.setCompletionPhase(NoCompletion);
        hint.addHintListener(hintListener);
        Disposer.register(this, () -> hint.removeHintListener(hintListener));
      }
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
      return indicator.nextInvocationCount(time, repeated);
    }
  }

  public static final class EmptyAutoPopup extends ZombiePhase {
    private final ActionTracker myTracker;
    private final Editor myEditor;
    private final Set<? extends Pair<Integer, ElementPattern<String>>> myRestartingPrefixConditions;

    EmptyAutoPopup(Editor editor, Set<? extends Pair<Integer, ElementPattern<String>>> restartingPrefixConditions) {
      super(null);
      myTracker = new ActionTracker(editor, this);
      myEditor = editor;
      myRestartingPrefixConditions = restartingPrefixConditions;
    }

    public boolean allowsSkippingNewAutoPopup(@NotNull Editor editor, char toType) {
      if (myEditor == editor &&
          !myTracker.hasAnythingHappened() &&
          !CompletionProgressIndicator.shouldRestartCompletion(editor, myRestartingPrefixConditions, String.valueOf(toType))) {
        myTracker.ignoreCurrentDocumentChange();
        return true;
      }
      return false;
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
      return time;
    }
  }
}
