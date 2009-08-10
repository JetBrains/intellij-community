/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.TestOnly;

import java.util.EventObject;

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
  private boolean myInitialized;
  private int myCount;
  private final Update myUpdate = new Update("update") {
    public void run() {
      updateLookup();
    }
  };
  private LightweightHint myHint;
  private final CompletionContext myContextOriginal;
  private final Semaphore myFreezeSemaphore;

  public CompletionProgressIndicator(final Editor editor, CompletionParameters parameters, CodeCompletionHandlerBase handler,
                                     final CompletionContext contextOriginal, Semaphore freezeSemaphore) {
    myEditor = editor;
    myParameters = parameters;
    myHandler = handler;
    myContextOriginal = contextOriginal;
    myFreezeSemaphore = freezeSemaphore;

    myLookup = (LookupImpl)LookupManager.getInstance(editor.getProject()).createLookup(editor, new LookupItem[0], "", new CompletionPreferencePolicy(
        parameters));

    myLookup.addLookupListener(new LookupAdapter() {
      public void itemSelected(LookupEvent event) {
        cancel();
        finishCompletion();

        LookupElement item = event.getItem();
        if (item == null) return;

        setMergeCommand();

        contextOriginal.setStartOffset(myEditor.getCaretModel().getOffset() - item.getLookupString().length());
        CodeCompletionHandlerBase.selectLookupItem(item, event.getCompletionChar(), contextOriginal, myLookup.getItems());
      }


      public void lookupCanceled(final LookupEvent event) {
        cancel();
        finishCompletion();
      }
    });
    myLookup.setCalculating(true);

    myQueue = new MergingUpdateQueue("completion lookup progress", 200, true, myEditor.getContentComponent());

    ApplicationManager.getApplication().assertIsDispatchThread();
    registerItself();

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        for (final CompletionContributor contributor : CompletionContributor.forParameters(myParameters)) {
          if (myLookup.getAdvertisementText() != null) return;
          if (!myLookup.isCalculating() && !myLookup.isVisible()) return;

          String s = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            public String compute() {
              return contributor.advertise(myParameters);
            }
          });
          if (myLookup.getAdvertisementText() != null) return;

          if (s != null) {
            myLookup.setAdvertisementText(s);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (myEditor.isDisposed() || myEditor.getComponent().getRootPane() == null) {
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

  public void liveAfterDeath(LightweightHint hint) {
    registerItself();
    myHint = hint;
    hint.addHintListener(new HintListener() {
      public void hintHidden(final EventObject event) {
        if (myHint != null) {
          cleanup();
        }
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
    if (myEditor.isDisposed() || myDisposed) return;

    if (!myInitialized) {
      myInitialized = true;
      if (myLookup.isCalculating()) {
        final AsyncProcessIcon processIcon = myLookup.getProcessIcon();
        processIcon.setVisible(true);
        processIcon.resume();
      }
      myLookup.show();
    }
    myLookup.refreshUi();
  }

  public int getCount() {
    return myCount;
  }

  public boolean willAutoInsert(final AutoCompletionPolicy policy, final PrefixMatcher matcher) {
    if (!myHandler.mayAutocompleteOnInvocation()) return false;

    if (policy == AutoCompletionPolicy.NEVER_AUTOCOMPLETE) return false;
    if (policy == AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE) return true;

    if (!isAutocompleteOnInvocation()) return false;

    if (isInsideIdentifier()) return false;
    if (policy == AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE) return true;

    if (StringUtil.isEmpty(matcher.getPrefix()) && myParameters.getCompletionType() != CompletionType.SMART) return false;
    return true;
  }

  private boolean isInsideIdentifier() {
    return myContextOriginal.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != myContextOriginal.getSelectionEndOffset();
  }

  private boolean isAutocompleteOnInvocation() {
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    switch (myParameters.getCompletionType()) {
      case CLASS_NAME:
        return settings.AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION;
      case SMART:
        return settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION;
      case BASIC:
        default:
        return settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
    }
  }


  public synchronized void addItem(final LookupElement item) {
    if (!isRunning()) return;
    ProgressManager.getInstance().checkCanceled();

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

  public void closeAndFinish() {
    if (myHint != null) {
      myHint.hide();
    }
    LookupManager.getInstance(myEditor.getProject()).hideActiveLookup();
  }

  private void finishCompletion() {
    assert !myDisposed;
    myDisposed = true;

    ApplicationManager.getApplication().assertIsDispatchThread();
    myQueue.dispose();
    cleanup();
  }

  @TestOnly
  public static void cleanupForNextTest() {
    CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (currentCompletion != null) {
      currentCompletion.finishCompletion();
    }
  }

  private void cleanup() {
    myHint = null;
    CompletionServiceImpl.getCompletionService().setCurrentCompletion(null);
  }

  public void stop() {
    myQueue.cancelAllUpdates();
    super.stop();

    invokeLaterIfNotDispatch(new Runnable() {
      public void run() {
        if (isCanceled()) return;

        if (myLookup.isVisible()) {
          myLookup.getProcessIcon().suspend();
          myLookup.getProcessIcon().setVisible(false);
          updateLookup();
        }
      }
    });

  }

  private void invokeLaterIfNotDispatch(final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread() || application.isUnitTestMode()) {
      runnable.run();
    } else {
      application.invokeLater(runnable, myQueue.getModalityState());
    }
  }

  public boolean fillInCommonPrefix(final boolean explicit) {
    if (isInsideIdentifier()) {
      return false;
    }

    final Boolean aBoolean = new WriteCommandAction<Boolean>(myEditor.getProject()) {
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

  public boolean isInitialized() {
    return myInitialized;
  }

}
