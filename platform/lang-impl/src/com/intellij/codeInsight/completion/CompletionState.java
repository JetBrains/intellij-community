package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author peter
 */
public class CompletionState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionState");
  private boolean myCompletionDisposed;
  private boolean myModifiersChanged;
  private volatile int myCount;

  public boolean isCompletionDisposed() {
    return myCompletionDisposed;
  }

  public void setCompletionDisposed(boolean completionDisposed) {
    LOG.assertTrue(!myCompletionDisposed, this);
    myCompletionDisposed = completionDisposed;
  }

  public boolean areModifiersChanged() {
    return myModifiersChanged;
  }

  public void modifiersChanged() {
    myModifiersChanged = true;
  }

  public void assertDisposed() {
    LOG.assertTrue(myCompletionDisposed, this);
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
           ", myModifiersReleased=" + myModifiersChanged +
           ", myCount=" + myCount +
           '}';
  }
}
