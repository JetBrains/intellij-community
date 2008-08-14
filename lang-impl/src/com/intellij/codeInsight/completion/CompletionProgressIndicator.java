/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import java.awt.*;
import java.util.EventObject;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author peter
 */
public class CompletionProgressIndicator extends ProgressIndicatorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionProgressIndicator");
  private static CompletionProgressIndicator ourCurrentCompletion = null;
  private static Throwable ourTrace = null;

  private final Editor myEditor;
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

  public CompletionProgressIndicator(final Editor editor, CompletionParameters parameters, String adText, CodeCompletionHandlerBase handler, final CompletionContext contextCopy, final CompletionContext contextOriginal) {
    myEditor = editor;
    myHandler = handler;

    myLookup = (LookupImpl)LookupManager.getInstance(editor.getProject()).createLookup(editor, new LookupItem[0], "", new CompletionPreferencePolicy(
        parameters), adText);

    myLookup.addLookupListener(new LookupAdapter() {
      public void itemSelected(LookupEvent event) {
        cancel();
        finishCompletion();

        LookupElement item = event.getItem();
        if (item == null) return;

        contextOriginal.setStartOffset(myEditor.getCaretModel().getOffset() - item.getLookupString().length());
        myHandler.selectLookupItem(item, CodeInsightSettings.getInstance().SHOW_SIGNATURES_IN_LOOKUPS ||
                                         (item instanceof LookupItem &&
                                          ((LookupItem)item).getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null),
                                   event.getCompletionChar(), contextOriginal, new LookupData(myLookup.getItems()));
      }

      public void lookupCanceled(final LookupEvent event) {
        cancel();
        finishCompletion();
      }
    });
    myLookup.setCalculating(true);

    myQueue = new MergingUpdateQueue("completion lookup progress", 2000, true, myEditor.getContentComponent());
    myQueue.queue(new Update("initialShow") {
        public void run() {
          final AsyncProcessIcon processIcon = getShownLookup().getProcessIcon();
          processIcon.setVisible(true);
          processIcon.resume();
          updateLookup();
        }
      });

    ApplicationManager.getApplication().assertIsDispatchThread();
    registerItself();
  }

  private void registerItself() {
    if (ourCurrentCompletion != null) {
      final StringWriter writer = new StringWriter();
      ourTrace.printStackTrace(new PrintWriter(writer));
      throw new RuntimeException("SHe's not dead yet!\nthis=" + this + "\ncurrent=" + ourCurrentCompletion + "\ntrace=" + writer.toString());
    }
    ourCurrentCompletion = this;
    ourTrace = new Throwable();
  }

  public void liveAfterDeath(LightweightHint hint) {
    registerItself();
    hint.addHintListener(new HintListener() {
      public void hintHidden(final EventObject event) {
        cleanup();
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

  public static CompletionProgressIndicator getCurrentCompletion() {
    return ourCurrentCompletion;
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
    LookupManager.getInstance(myEditor.getProject()).hideActiveLookup();
  }

  private void finishCompletion() {
    assert !myDisposed;
    myDisposed = true;

    ApplicationManager.getApplication().assertIsDispatchThread();
    myQueue.dispose();
    cleanup();
  }

  private void cleanup() {
    assert ourCurrentCompletion == this;
    ourCurrentCompletion = null;
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

  public boolean fillInCommonPrefix() {
    return new WriteCommandAction<Boolean>(myEditor.getProject()) {
      protected void run(Result<Boolean> result) throws Throwable {
        result.setResult(myLookup.fillInCommonPrefix(true));
      }
    }.execute().getResultObject().booleanValue();
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  public boolean isActive(CodeCompletionHandlerBase handler) {
    return getHandler().getClass().equals(handler.getClass()) && !isCanceled();
  }
}
