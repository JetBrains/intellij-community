package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * @author cdr
*/
class MockDaemonProgressIndicator extends DaemonProgressIndicator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.MockDaemonProgressIndicator");
  final Object myStoppedNotify;

  public MockDaemonProgressIndicator(final Object stoppedNotify) {
    myStoppedNotify = stoppedNotify;
  }

  public void stop() {
    myIsRunning = false;
    super.stop();
    if (LOG.isDebugEnabled()) {
      LOG.debug("STOPPED", new Throwable());
    }
    synchronized(myStoppedNotify) {
      myStoppedNotify.notifyAll();
    }
  }
  private boolean myIsRunning = false;
  private boolean myIsCanceled = false;

  public void start() {
    myIsRunning = true;
    myIsCanceled = false;
  }

  public boolean isRunning() {
    return myIsRunning;
  }

  public void cancel() {
    myIsCanceled = true;
  }

  public boolean isCanceled() {
    return myIsCanceled;
  }

  public void setText(String text) {
  }

  public String getText() {
    return "";
  }

  public void setText2(String text) {
  }

  public String getText2() {
    return "";
  }

  public double getFraction() {
    return 1;
  }

  public void setFraction(double fraction) {
  }

  public void pushState() {
  }

  public void popState() {
  }

  public void startNonCancelableSection() {
  }

  public void finishNonCancelableSection() {
  }

  public void setModalityProgress(ProgressIndicator modalityProgress) {
  }

  public boolean isIndeterminate() {
    return false;
  }

  public void setIndeterminate(boolean indeterminate) {
  }
}
