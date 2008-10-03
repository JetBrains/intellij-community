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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
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

import java.awt.*;
import java.util.EventObject;

/**
 * @author peter
 */
public class CompletionProgressIndicator extends ProgressIndicatorBase implements CompletionProcess{
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
  private Semaphore myFreezeSemaphore;

  public CompletionProgressIndicator(final Editor editor, CompletionParameters parameters, CodeCompletionHandlerBase handler,
                                     final CompletionContext contextOriginal, Semaphore freezeSemaphore) {
    myEditor = editor;
    myParameters = parameters;
    myHandler = handler;
    myContextOriginal = contextOriginal;
    myFreezeSemaphore = freezeSemaphore;

    myLookup = (LookupImpl)LookupManager.getInstance(editor.getProject()).createLookup(editor, new LookupItem[0], "", new CompletionPreferencePolicy(
        parameters), null);

    myLookup.addLookupListener(new LookupAdapter() {
      public void itemSelected(LookupEvent event) {
        cancel();
        finishCompletion();

        LookupElement item = event.getItem();
        if (item == null) return;

        contextOriginal.setStartOffset(myEditor.getCaretModel().getOffset() - item.getLookupString().length());
        CodeCompletionHandlerBase.selectLookupItem(item, CodeInsightSettings.getInstance().SHOW_SIGNATURES_IN_LOOKUPS ||
                                                         (item instanceof LookupItem &&
                                                          ((LookupItem)item).getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null),
                                                   event.getCompletionChar(), contextOriginal, myLookup.getItems());
      }

      public void lookupCanceled(final LookupEvent event) {
        cancel();
        finishCompletion();
      }
    });
    myLookup.setCalculating(true);

    myQueue = new MergingUpdateQueue("completion lookup progress", 2000, true, myEditor.getContentComponent());

    ApplicationManager.getApplication().assertIsDispatchThread();
    registerItself();

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
          if (!isRunning() || myLookup.getAdvertisementText() != null) return;
          final String s = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            public String compute() {
              return contributor.advertise(myParameters);
            }
          });
          if (s != null) {
            myLookup.setAdvertisementText(s);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                updateLookup();
              }
            });
            return;
          }
        }
      }
    });
  }

  public void showLookup() {
    if (myLookup.isCalculating()) {
      final AsyncProcessIcon processIcon = myLookup.getProcessIcon();
      processIcon.setVisible(true);
      processIcon.resume();
    }
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
    if (myEditor.isDisposed()) return;

    myFreezeSemaphore.up();
    myLookup.updateList();
    if (!myInitialized) {
      myInitialized = true;
      myLookup.show();
    }
    else if (myLookup.isVisible()) {
      Point point=myLookup.calculatePosition();
      Dimension preferredSize = myLookup.getComponent().getPreferredSize();
      myLookup.setBounds(point.x,point.y,preferredSize.width,preferredSize.height);
    }
    myLookup.adaptSize();
  }

  public int getCount() {
    return myCount;
  }

  public boolean willAutoInsert(final AutoCompletionPolicy policy, final PrefixMatcher matcher) {
    if (policy == AutoCompletionPolicy.NEVER_AUTOCOMPLETE) return false;
    if (policy == AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE) return true;

    if (myHandler.mayAutocompleteOnInvocation()) {
      if (!isAutocompleteOnInvocation()) return false;
    }

    if (myContextOriginal.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != myContextOriginal.getSelectionEndOffset()) return false;
    if (policy == AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE) return true;

    if (StringUtil.isEmpty(matcher.getPrefix()) && myParameters.getCompletionType() != CompletionType.SMART) return false;
    return true;
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

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      assert !ApplicationManager.getApplication().isDispatchThread();
    }

    myLookup.addItem(item);
    myCount++;
    if (myCount == 1) {
      myQueue.setMergingTimeSpan(200);
    } else {
      myQueue.queue(myUpdate);
    }
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
    return new WriteCommandAction<Boolean>(myEditor.getProject()) {
      protected void run(Result<Boolean> result) throws Throwable {
        result.setResult(myLookup.fillInCommonPrefix(explicit));
      }
    }.execute().getResultObject().booleanValue();
  }

  public boolean isInitialized() {
    return myInitialized;
  }

}
