/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupAdapter;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Computable;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import java.awt.*;

/**
 * @author peter
 */
public class CompletionProgressIndicator extends ProgressIndicatorBase implements Disposable {
  private static CompletionProgressIndicator ourCurrentCompletion = null;

  private final Editor myEditor;
  private final CodeCompletionHandlerBase myHandler;
  private final LookupImpl myLookup;
  private final MergingUpdateQueue myQueue;
  private boolean myDisposed;
  private boolean myInitialized;
  private int myCount;

  public CompletionProgressIndicator(final Editor editor, CompletionParameters parameters, String adText, CodeCompletionHandlerBase handler, final CompletionContext completionContext) {
    myEditor = editor;
    myHandler = handler;

    final String prefix = CompletionData.findPrefixStatic(parameters.getPosition(), completionContext.getStartOffset());
    myLookup = (LookupImpl)LookupManager.getInstance(editor.getProject()).createLookup(editor, new LookupItem[0], prefix, new CompletionPreferencePolicy(
        prefix, parameters), adText);

    Disposer.register(this, myLookup);
    myLookup.addLookupListener(new LookupAdapter() {
      public void itemSelected(LookupEvent event) {
        cancel();

        LookupItem item = event.getItem();
        if (item == null) return;

        myHandler.selectLookupItem(item, CodeInsightSettings.getInstance().SHOW_SIGNATURES_IN_LOOKUPS || item.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null,
                                   event.getCompletionChar(), completionContext, new LookupData(myLookup.getItems(), completionContext.getPrefix()));
      }

      public void lookupCanceled(final LookupEvent event) {
        cancel();
      }
    });
    myLookup.setCalculating(true);

    myQueue = new MergingUpdateQueue("completion lookup progress", 400, true, myEditor.getContentComponent());
    Disposer.register(this, myQueue);

    ApplicationManager.getApplication().assertIsDispatchThread();
    assert ourCurrentCompletion == null;
    ourCurrentCompletion = this;
  }

  public void start() {
    super.start();
    myQueue.queue(new Update("initialShow") {
      public void run() {
        final AsyncProcessIcon processIcon = getShownLookup().getProcessIcon();
        processIcon.setVisible(true);
        processIcon.resume();
        updateLookup();
        myQueue.setMergingTimeSpan(200);
      }
    });
  }

  public CodeCompletionHandlerBase getHandler() {
    return myHandler;
  }

  public LookupImpl getShownLookup() {
    updateLookup();
    return myLookup;
  }

  public LookupImpl getLookup() {
    return myLookup;
  }

  private void updateLookup() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myInitialized) {
      myInitialized = true;
      myLookup.show();
    }
    if (myLookup.isVisible()) {
      Point point=myLookup.calculatePosition();
      Dimension preferredSize = myLookup.getComponent().getPreferredSize();
      myLookup.setBounds(point.x,point.y,preferredSize.width,preferredSize.height);
    }
    myLookup.updateList();
    myLookup.adaptSize();
  }

  public int getCount() {
    return myCount;
  }

  public static CompletionProgressIndicator getCurrentCompletion() {
    return ourCurrentCompletion;
  }

  public void addItem(final LookupItem item) {
    if (!isRunning()) return;
    ProgressManager.getInstance().checkCanceled();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      assert !ApplicationManager.getApplication().isDispatchThread();
    }

    myLookup.addItem(item);
    myCount++;
    if (myCount > 42) {
      myQueue.queue(new Update("update") {
        public void run() {
          updateLookup();
          myQueue.setMergingTimeSpan(200);
        }
      });
    }
  }

  public void cancel() {
    myQueue.cancelAllUpdates();
    invokeLaterIfNotDispatch(new Runnable() {
      public void run() {
        LookupManagerImpl.getInstance(myEditor.getProject()).hideActiveLookup();
        finishCompletion();
      }
    });
    super.cancel();
  }

  public void closeAndFinish() {
    LookupManager.getInstance(myEditor.getProject()).hideActiveLookup();
    finishCompletion();
  }

  private void finishCompletion() {
    Disposer.dispose(this);
  }

  public void stop() {
    myQueue.cancelAllUpdates();
    super.stop();

    invokeLaterIfNotDispatch(new Runnable() {
      public void run() {
        if (isCanceled()) return;

        myLookup.setCalculating(false);
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

  public void dispose() {
    if (myDisposed) return;
    myDisposed = true;

    ApplicationManager.getApplication().assertIsDispatchThread();
    assert ourCurrentCompletion == this || ourCurrentCompletion == null;
    ourCurrentCompletion = null;
  }

  public boolean fillInCommonPrefix() {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
      public Boolean compute() {
        return myLookup != null && myLookup.fillInCommonPrefix(true);
      }
    });
  }

  public boolean isInitialized() {
    return myInitialized;
  }
}
