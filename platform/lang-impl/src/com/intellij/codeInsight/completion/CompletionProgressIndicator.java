/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.lookup.LookupAdapter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author peter
 */
public class CompletionProgressIndicator extends ProgressIndicatorBase implements CompletionProcess{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionProgressIndicator");
  private final Editor myEditor;
  private final CompletionParameters myParameters;
  private final CodeCompletionHandlerBase myHandler;
  private final LookupImpl myLookup;
  private final MergingUpdateQueue myQueue;
  private boolean myDisposed;
  private boolean myShownLookup;
  private volatile int myCount;
  private final Update myUpdate = new Update("update") {
    public void run() {
      updateLookup();
      myQueue.setMergingTimeSpan(100);
    }
  };
  private LightweightHint myHint;
  private final Semaphore myFreezeSemaphore;
  private Boolean myToRestart;

  private boolean myModifiersReleased;

  private String myOldDocumentText;
  private int myOldCaret;
  private int myOldStart;
  private int myOldEnd;
  private boolean myBackgrounded = true;
  private OffsetMap myOffsetMap;
  private final CopyOnWriteArrayList<Pair<Integer, ElementPattern<String>>> myRestartingPrefixConditions = ContainerUtil.createEmptyCOWList();
  private final LookupAdapter myLookupListener = new LookupAdapter() {
    public void itemSelected(LookupEvent event) {
      ensureDuringCompletionPassed();

      finishCompletionProcess();

      LookupElement item = event.getItem();
      if (item == null) return;

      setMergeCommand();

      myOffsetMap.addOffset(CompletionInitializationContext.START_OFFSET, myEditor.getCaretModel().getOffset() - item.getLookupString().length());
      CodeCompletionHandlerBase.selectLookupItem(item, event.getCompletionChar(), CompletionProgressIndicator.this, myLookup.getItems());
    }


    public void lookupCanceled(final LookupEvent event) {
      finishCompletionProcess();
    }
  };
  private final Semaphore myDuringCompletionSemaphore = new Semaphore();
  private volatile boolean myFocusLookupWhenDone;

  public CompletionProgressIndicator(final Editor editor, CompletionParameters parameters, CodeCompletionHandlerBase handler, Semaphore freezeSemaphore,
                                     final OffsetMap offsetMap, LookupImpl lookup) {
    myEditor = editor;
    myParameters = parameters;
    myHandler = handler;
    myFreezeSemaphore = freezeSemaphore;
    myOffsetMap = offsetMap;
    myLookup = lookup;

    myLookup.setArranger(new CompletionLookupArranger(parameters));
    myShownLookup = lookup.isShown();

    myLookup.addLookupListener(myLookupListener);
    myLookup.setCalculating(true);

    myQueue = new MergingUpdateQueue("completion lookup progress", 200, true, myEditor.getContentComponent());

    ApplicationManager.getApplication().assertIsDispatchThread();
    registerItself();

    if (!ApplicationManager.getApplication().isUnitTestMode() && !lookup.isShown()) {
      scheduleAdvertising();
    }

    trackModifiers();

    myDuringCompletionSemaphore.down();
  }

  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  public int getSelectionEndOffset() {
    return getOffsetMap().getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }

  void notifyBackgrounded() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myBackgrounded = true;
  }

  boolean isBackgrounded() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myBackgrounded;
  }

  void duringCompletion(CompletionInitializationContext initContext) {
    try {
      ProgressManager.checkCanceled();

      if (!initContext.getOffsetMap().wasModified(CompletionInitializationContext.IDENTIFIER_END_OFFSET)) {
        try {
          final int selectionEndOffset = initContext.getSelectionEndOffset();
          final PsiReference reference = initContext.getFile().findReferenceAt(selectionEndOffset);
          if (reference != null) {
            initContext.setReplacementOffset(findReplacementOffset(selectionEndOffset, reference));
          }
        }
        catch (IndexNotReadyException ignored) {
        }
      }

      for (CompletionContributor contributor : CompletionContributor.forLanguage(initContext.getPositionLanguage())) {
        if (DumbService.getInstance(initContext.getProject()).isDumb() && !DumbService.isDumbAware(contributor)) {
          continue;
        }

        contributor.duringCompletion(initContext);
      }
    } catch (ProcessCanceledException ignored) {
    } finally {
      myDuringCompletionSemaphore.up();
    }
  }

  void ensureDuringCompletionPassed() {
    myDuringCompletionSemaphore.waitFor();
  }

  public void setFocusLookupWhenDone(boolean focusLookup) {
    myFocusLookupWhenDone = focusLookup;
  }

  private static int findReplacementOffset(int selectionEndOffset, PsiReference reference) {
    final List<TextRange> ranges = ReferenceRange.getAbsoluteRanges(reference);
    for (TextRange range : ranges) {
      if (range.contains(selectionEndOffset)) {
        return range.getEndOffset();
      }
    }

    return reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset();
  }


  private void scheduleAdvertising() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        if (isOutdated()) return; //tests?
        final List<CompletionContributor> list = ApplicationManager.getApplication().runReadAction(new Computable<List<CompletionContributor>>() {
          public List<CompletionContributor> compute() {
            if (isOutdated()) {
              return Collections.emptyList();
            }

            return CompletionContributor.forParameters(myParameters);
          }
        });
        for (final CompletionContributor contributor : list) {
          if (myLookup.getAdvertisementText() != null) return;
          if (!myLookup.isCalculating() && !myLookup.isVisible()) return;

          String s = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            @Nullable
            public String compute() {
              if (isOutdated()) {
                return null;
              }

              return contributor.advertise(myParameters);
            }
          });
          if (myLookup.getAdvertisementText() != null) return;

          if (s != null) {
            myLookup.setAdvertisementText(s);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (isOutdated()) {
                  return;
                }
                if (isAutopopupCompletion() && !myShownLookup) {
                  return;
                }
                if (!isBackgrounded()) {
                  return;
                }
                updateLookup();
              }
            }, myQueue.getModalityState());
            return;
          }
        }
      }
    });
  }

  private boolean isOutdated() {
    return myDisposed ||
           myEditor.isDisposed() ||
           !ApplicationManager.getApplication().isUnitTestMode() && myEditor.getComponent().getRootPane() == null;
  }

  private void trackModifiers() {
    if (isAutopopupCompletion()) {
      return;
    }

    final JComponent contentComponent = myEditor.getContentComponent();
    contentComponent.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        processModifier(e);
      }

      public void keyReleased(KeyEvent e) {
        processModifier(e);
      }

      private void processModifier(KeyEvent e) {
        final int code = e.getKeyCode();
        if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT || code == KeyEvent.VK_SHIFT) {
          myModifiersReleased = true;
          if (myOldDocumentText != null) {
            cleanup();
          }
          contentComponent.removeKeyListener(this);
        }
      }
    });
  }

  private void setMergeCommand() {
    CommandProcessor.getInstance().setCurrentCommandGroupId("Completion" + hashCode());
  }

  public void showLookup() {
    updateLookup();
  }

  public CompletionParameters getParameters() {
    return myParameters;
  }

  private void registerItself() {
    CompletionServiceImpl.getCompletionService().setCurrentCompletion(this);
  }

  public void liveAfterDeath(@Nullable final LightweightHint hint) {
    if (myModifiersReleased || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    registerItself();
    myHint = hint;
    if (hint != null) {
      hint.addHintListener(new HintListener() {
        public void hintHidden(final EventObject event) {
          hint.removeHintListener(this);
          cleanup();
        }
      });
    }
    final Document document = myEditor.getDocument();
    document.addDocumentListener(new DocumentAdapter() {
      @Override
      public void beforeDocumentChange(DocumentEvent e) {
        document.removeDocumentListener(this);
        cleanup();
      }
    });
    final SelectionModel selectionModel = myEditor.getSelectionModel();
    selectionModel.addSelectionListener(new SelectionListener() {
      public void selectionChanged(SelectionEvent e) {
        selectionModel.removeSelectionListener(this);
        cleanup();
      }
    });
    final CaretModel caretModel = myEditor.getCaretModel();
    caretModel.addCaretListener(new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        caretModel.removeCaretListener(this);
        cleanup();
      }
    });
  }

  public CodeCompletionHandlerBase getHandler() {
    return myHandler;
  }

  public LookupImpl getLookup() {
    return myLookup;
  }

  private void updateLookup() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isOutdated()) return;

    if (!myShownLookup) {
      myShownLookup = true;

      if (StringUtil.isEmpty(myLookup.getAdvertisementText()) && !isAutopopupCompletion()) {
        final String text = DefaultCompletionContributor.getDefaultAdvertisementText(myParameters);
        if (text != null) {
          myLookup.setAdvertisementText(text);
        }
      }

      myLookup.show();
    }
    myLookup.refreshUi();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.assertTrue(myLookup.isVisible());
    }
  }

  final boolean isInsideIdentifier() {
    return getIdentifierEndOffset() != getSelectionEndOffset();
  }

  public int getIdentifierEndOffset() {
    return myOffsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
  }


  public synchronized void addItem(final LookupElement item) {
    if (!isRunning()) return;
    ProgressManager.checkCanceled();

    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (!unitTestMode) {
      assert !ApplicationManager.getApplication().isDispatchThread();
    }

    myLookup.addItem(item);
    myCount++;
    if (unitTestMode) return;

    if (myCount == 1) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          try {
            Thread.sleep(300);
          }
          catch (InterruptedException e) {
            LOG.error(e);
          }
          myFreezeSemaphore.up();
        }
      });
    }
    myQueue.queue(myUpdate);
  }

  public void closeAndFinish(boolean hideLookup) {
    if (myHint != null) {
      myHint.hide();
    }

    if (LookupManager.getActiveLookup(myEditor) == myLookup) {
      myLookup.removeLookupListener(myLookupListener);
      finishCompletionProcess();

      if (hideLookup) {
        LookupManager.getInstance(getProject()).hideActiveLookup();
      }
    }

  }

  private void finishCompletionProcess() {
    myToRestart = false;
    cancel();

    assert !myDisposed;
    myDisposed = true;

    ApplicationManager.getApplication().assertIsDispatchThread();
    Disposer.dispose(myQueue);
    cleanup();
  }

  @TestOnly
  public static void cleanupForNextTest() {
    CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (currentCompletion != null) {
      currentCompletion.finishCompletionProcess();
    }
  }

  private void cleanup() {
    assert ApplicationManager.getApplication().isDispatchThread();
    myHint = null;
    myOldDocumentText = null;
    unregisterItself();
  }

  private static void unregisterItself() {
    CompletionServiceImpl.getCompletionService().setCurrentCompletion(null);
  }

  public void stop() {
    super.stop();

    myQueue.cancelAllUpdates();
    myFreezeSemaphore.up();

    invokeLaterIfNotDispatch(new Runnable() {
      public void run() {
        if (isOutdated()) return;
        if (isCanceled() && myToRestart != Boolean.TRUE) return;

        //what if a new completion was invoked by the user before this 'later'?
        if (CompletionProgressIndicator.this != CompletionServiceImpl.getCompletionService().getCurrentCompletion()) return;

        myLookup.setCalculating(false);

        if (!isBackgrounded()) return;

        if (hideAutopopupIfMeaningless()) {
          return;
        }

        if (myCount == 0) {
          LookupManager.getInstance(getProject()).hideActiveLookup();

          final CompletionProgressIndicator current = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
          assert current == null : current + "!=" + CompletionProgressIndicator.this;

          if (!isAutopopupCompletion() ) {
            myHandler.handleEmptyLookup(getProject(), myEditor, myParameters, CompletionProgressIndicator.this);
          }
        } else {
          if (myFocusLookupWhenDone) {
            myLookup.setFocused(true);
          }
          updateLookup();
        }

      }
    });

  }

  public boolean hideAutopopupIfMeaningless() {
    if (isAutopopupCompletion() && !myLookup.isCalculating()) {
      myLookup.refreshUi();
      final List<LookupElement> items = myLookup.getItems();
      if (items.size() == 0 || items.size() == 1 && (items.get(0).getPrefixMatcher().getPrefix() + myLookup.getAdditionalPrefix()).equals(items.get(0).getLookupString())) {
        myLookup.hideLookup(false);
        assert CompletionServiceImpl.getCompletionService().getCurrentCompletion() == null;
        return true;
      }
    }
    return false;
  }

  private void invokeLaterIfNotDispatch(final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      runnable.run();
    } else {
      application.invokeLater(runnable, myQueue.getModalityState());
    }
  }

  public void cancelByWriteAction() {
    if (myToRestart != null) {
      LOG.assertTrue(myToRestart == Boolean.FALSE); //explicit completionFinished was invoked before this write action
      return;
    }

    myToRestart = true;
    cancel();

    scheduleRestart();
  }

  public boolean fillInCommonPrefix(final boolean explicit) {
    if (isInsideIdentifier()) {
      return false;
    }

    final Boolean aBoolean = new WriteCommandAction<Boolean>(getProject()) {
      protected void run(Result<Boolean> result) throws Throwable {
        if (!explicit) {
          setMergeCommand();
        }
        try {
          result.setResult(myLookup.fillInCommonPrefix(explicit));
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }.execute().getResultObject();
    return aBoolean.booleanValue();
  }

  public void restorePrefix() {
    setMergeCommand();

    if (myOldDocumentText != null) {
      myEditor.getDocument().setText(myOldDocumentText);
      myEditor.getSelectionModel().setSelection(myOldStart, myOldEnd);
      myEditor.getCaretModel().moveToOffset(myOldCaret);
      myOldDocumentText = null;
      return;
    }

    getLookup().restorePrefix();
  }

  public Editor getEditor() {
    return myEditor;
  }

  public void rememberDocumentState() {
    if (myModifiersReleased) {
      return;
    }

    myOldDocumentText = myEditor.getDocument().getText();
    myOldCaret = myEditor.getCaretModel().getOffset();
    myOldStart = myEditor.getSelectionModel().getSelectionStart();
    myOldEnd = myEditor.getSelectionModel().getSelectionEnd();
  }

  public boolean isRepeatedInvocation(CompletionType completionType, Editor editor) {
    return completionType == myParameters.getCompletionType() && editor == myEditor;
  }

  @Override
  public boolean isAutopopupCompletion() {
    return myHandler.autopopup;
  }

  @NotNull
  public Project getProject() {
    return ObjectUtils.assertNotNull(myEditor.getProject());
  }

  public void addWatchedPrefix(int startOffset, ElementPattern<String> restartCondition) {
    if (isAutopopupCompletion()) {
      myRestartingPrefixConditions.add(Pair.create(startOffset, restartCondition));
    }
  }

  public void prefixUpdated() {
    if (myToRestart == Boolean.TRUE) {
      scheduleRestart();
      return;
    }

    if (CompletionAutoPopupHandler.ourTestingAutopopup) {
      System.out.println("CompletionProgressIndicator.prefixUpdated");
    }

    final CharSequence text = myEditor.getDocument().getCharsSequence();
    final int caretOffset = myEditor.getCaretModel().getOffset();
    for (Pair<Integer, ElementPattern<String>> pair : myRestartingPrefixConditions) {
      final String newPrefix = text.subSequence(pair.first, caretOffset).toString();
      if (CompletionAutoPopupHandler.ourTestingAutopopup) {
        System.out.println("newPrefix = " + newPrefix);
      }
      if (pair.second.accepts(newPrefix)) {
        scheduleRestart();
        myRestartingPrefixConditions.clear();
        return;
      }
    }

    hideAutopopupIfMeaningless();
  }

  public void scheduleRestart() {
    if (CompletionAutoPopupHandler.ourTestingAutopopup) {
      System.out.println("CompletionProgressIndicator.scheduleRestart");
    }


    ApplicationManager.getApplication().assertIsDispatchThread();

    final int offset = myEditor.getCaretModel().getOffset();
    final long stamp = myEditor.getDocument().getModificationStamp();
    final Project project = getProject();
    final boolean wasDumb = DumbService.getInstance(project).isDumb();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (CompletionAutoPopupHandler.ourTestingAutopopup) {
          System.out.println("later");
        }

        if (myEditor.isDisposed() || project.isDisposed()) {
          return;
        }

        if (!wasDumb && DumbService.getInstance(project).isDumb()) {
          return;
        }

        if (myEditor.getCaretModel().getOffset() != offset || myEditor.getDocument().getModificationStamp() != stamp) {
          return;
        }

        if (CompletionAutoPopupHandler.ourTestingAutopopup) {
          System.out.println("invoking");
        }


        if (!isOutdated()) {
          closeAndFinish(false);
        }

        final CodeCompletionHandlerBase newHandler = new CodeCompletionHandlerBase(myParameters.getCompletionType(), false, isAutopopupCompletion());
        final PsiFile psiFileInEditor = PsiUtilBase.getPsiFileInEditor(myEditor, project);
        try {
          newHandler.invokeCompletion(project, myEditor, psiFileInEditor, myParameters.getInvocationCount());
        }
        catch (IndexNotReadyException ignored) {
        }
      }
    });
  }
}
