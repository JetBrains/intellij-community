package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author peter
 */
public class CompletionState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionState");
  private boolean myCompletionDisposed;
  private boolean myShownLookup;
  private Boolean myToRestart;
  private boolean myRestartScheduled;
  private boolean myModifiersChanged;
  private Runnable myRestorePrefix;
  private boolean myBackgrounded;
  private volatile boolean myFocusLookupWhenDone;
  private volatile int myCount;

  public CompletionState(boolean shownLookup) {
    myShownLookup = shownLookup;
  }

  public boolean isCompletionDisposed() {
    return myCompletionDisposed;
  }

  public void setCompletionDisposed(boolean completionDisposed) {
    LOG.assertTrue(!myCompletionDisposed, this);
    LOG.assertTrue(!isWaitingAfterAutoInsertion(), this);
    myCompletionDisposed = completionDisposed;
  }

  public boolean isShownLookup() {
    return myShownLookup;
  }

  public void setShownLookup(boolean shownLookup) {
    myShownLookup = shownLookup;
  }

  public boolean isToRestart() {
    return myToRestart == Boolean.TRUE;
  }

  public void setToRestart(boolean toRestart) {
    if (toRestart && myToRestart != null) {
      LOG.assertTrue(myToRestart == Boolean.FALSE, this); //explicit completionFinished was invoked before this write action
      return;
    }

    myToRestart = toRestart;
  }

  public boolean isRestartScheduled() {
    return myRestartScheduled;
  }

  public void scheduleRestart() {
    myRestartScheduled = true;
  }

  public boolean areModifiersChanged() {
    return myModifiersChanged;
  }

  public void modifiersChanged() {
    myModifiersChanged = true;
  }

  public boolean isWaitingAfterAutoInsertion() {
    CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    if (phase instanceof CompletionPhase.InsertedSingleItem && ((CompletionPhase.InsertedSingleItem)phase).indicator.getCompletionState() == this) {
      LOG.assertTrue(myRestorePrefix != null, this);
      return true;
    }
    return false;
  }

  public void setRestorePrefix(Runnable restorePrefix) {
    CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());
    myRestorePrefix = restorePrefix;
  }

  public boolean isBackgrounded() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myBackgrounded;
  }

  public void setBackgrounded() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myBackgrounded = true;
  }

  public boolean isFocusLookupWhenDone() {
    return myFocusLookupWhenDone;
  }

  public void setFocusLookupWhenDone(boolean focusLookupWhenDone) {
    myFocusLookupWhenDone = focusLookupWhenDone;
  }

  public void assertDisposed() {
    LOG.assertTrue(myCompletionDisposed, this);
  }

  public void restorePrefix() {
    CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());
    if (myRestorePrefix != null) {
      myRestorePrefix.run();
      myRestorePrefix = null;
    }
  }

  public void handleDeath() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assertDisposed();
    myRestorePrefix = null;
  }

  int incCount() {
    return ++myCount;
  }

  boolean hasNoVariants() {
    return myCount == 0;
  }

  @Override
  public String toString() {
    return "CompletionState{" +
           "phase=" + CompletionServiceImpl.getCompletionPhase() +
           ", myCompletionDisposed=" + myCompletionDisposed +
           ", myShownLookup=" + myShownLookup +
           ", myToRestart=" + myToRestart +
           ", myRestartScheduled=" + myRestartScheduled +
           ", myModifiersReleased=" + myModifiersChanged +
           ", myRestorePrefix=" + myRestorePrefix +
           ", myBackgrounded=" + myBackgrounded +
           ", myFocusLookupWhenDone=" + myFocusLookupWhenDone +
           ", myCount=" + myCount +
           '}';
  }
}
