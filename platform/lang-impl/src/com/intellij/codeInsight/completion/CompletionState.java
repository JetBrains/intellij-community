package com.intellij.codeInsight.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.LightweightHint;

/**
 * @author peter
 */
public class CompletionState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionState");
  private boolean myCompletionDisposed;
  private boolean myShownLookup;
  private LightweightHint myCompletionHint;
  private Boolean myToRestart;
  private boolean myRestartScheduled;
  private boolean myModifiersReleased;
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
    myCompletionDisposed = completionDisposed;
  }

  public boolean isShownLookup() {
    return myShownLookup;
  }

  public void setShownLookup(boolean shownLookup) {
    myShownLookup = shownLookup;
  }

  public LightweightHint getCompletionHint() {
    return myCompletionHint;
  }

  public void setCompletionHint(LightweightHint completionHint) {
    myCompletionHint = completionHint;
  }

  public boolean isToRestart() {
    return myToRestart == Boolean.TRUE;
  }

  public void setToRestart(boolean toRestart) {
    if (toRestart) {
      if (myToRestart != null) {
        LOG.assertTrue(myToRestart == Boolean.FALSE, this); //explicit completionFinished was invoked before this write action
        return;
      }
    }

    myToRestart = toRestart;
  }

  public boolean isRestartScheduled() {
    return myRestartScheduled;
  }

  public void setRestartScheduled(boolean restartScheduled) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myRestartScheduled = restartScheduled;
  }

  public boolean isModifiersReleased() {
    return myModifiersReleased;
  }

  public void setModifiersReleased(boolean modifiersReleased) {
    myModifiersReleased = modifiersReleased;
  }

  public boolean isWaitingAfterAutoInsertion() {
    return myRestorePrefix != null;
  }

  public void setRestorePrefix(Runnable restorePrefix) {
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
    if (myRestorePrefix != null) {
      myRestorePrefix.run();
      myRestorePrefix = null;
    }
  }

  public void handleDeath() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assertDisposed();
    setCompletionHint(null);
    setRestorePrefix(null);
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
           "myCompletionDisposed=" + myCompletionDisposed +
           ", myShownLookup=" + myShownLookup +
           ", myCompletionHint=" + myCompletionHint +
           ", myToRestart=" + myToRestart +
           ", myRestartScheduled=" + myRestartScheduled +
           ", myModifiersReleased=" + myModifiersReleased +
           ", myRestorePrefix=" + myRestorePrefix +
           ", myBackgrounded=" + myBackgrounded +
           ", myFocusLookupWhenDone=" + myFocusLookupWhenDone +
           ", myCount=" + myCount +
           '}';
  }
}
