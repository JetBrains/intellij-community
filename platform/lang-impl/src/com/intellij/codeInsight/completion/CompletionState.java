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
  private boolean myModifiersChanged;
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

  public boolean areModifiersChanged() {
    return myModifiersChanged;
  }

  public void modifiersChanged() {
    myModifiersChanged = true;
  }

  public boolean isWaitingAfterAutoInsertion() {
    CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    if (phase instanceof CompletionPhase.InsertedSingleItem && ((CompletionPhase.InsertedSingleItem)phase).indicator.getCompletionState() == this) {
      return true;
    }
    return false;
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

  public void handleDeath() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assertDisposed();
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
           ", myModifiersReleased=" + myModifiersChanged +
           ", myBackgrounded=" + myBackgrounded +
           ", myFocusLookupWhenDone=" + myFocusLookupWhenDone +
           ", myCount=" + myCount +
           '}';
  }
}
