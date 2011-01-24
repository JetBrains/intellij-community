package com.intellij.codeInsight.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private boolean myModifiersChanged;
  private Runnable myRestorePrefix;
  private boolean myBackgrounded;
  private volatile boolean myFocusLookupWhenDone;
  private volatile int myCount;
  private Runnable myZombieCleanup;

  public CompletionState(boolean shownLookup) {
    myShownLookup = shownLookup;
  }

  public boolean isCompletionDisposed() {
    return myCompletionDisposed;
  }

  public void setCompletionDisposed(boolean completionDisposed) {
    LOG.assertTrue(!myCompletionDisposed, this);
    LOG.assertTrue(!isWaitingAfterAutoInsertion(), this);
    LOG.assertTrue(myCompletionHint == null, this);
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

  public void goZombie(@Nullable LightweightHint completionHint, @NotNull Runnable cleanup) {
    LOG.assertTrue(myZombieCleanup == null, this);
    if (completionHint != null) {
      LOG.assertTrue(myCompletionHint == null, this);
    } else {
      LOG.assertTrue(!myModifiersChanged, this);
    }
    myCompletionHint = completionHint;
    myZombieCleanup = cleanup;
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
    return myRestorePrefix != null && isZombie();
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

  public void handleDeath(boolean afterDeath) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean zombie = isZombie();
    LOG.assertTrue(afterDeath == zombie, this);
    assertDisposed();
    if (zombie) {
      myZombieCleanup.run();
    }
    myZombieCleanup = null;
    myCompletionHint = null;
    setRestorePrefix(null);
  }

  public boolean isZombie() {
    return myZombieCleanup != null;
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
           ", myModifiersReleased=" + myModifiersChanged +
           ", myRestorePrefix=" + myRestorePrefix +
           ", myBackgrounded=" + myBackgrounded +
           ", myFocusLookupWhenDone=" + myFocusLookupWhenDone +
           ", myCount=" + myCount +
           ", myZombieCleanup=" + myZombieCleanup +
           '}';
  }
}
