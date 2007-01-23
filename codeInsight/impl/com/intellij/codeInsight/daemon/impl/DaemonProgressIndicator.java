package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.progress.util.ProgressIndicatorBase;

/**
 * @author cdr
 */
public class DaemonProgressIndicator extends ProgressIndicatorBase {
  public synchronized void stop() {
    super.stop();
    cancel();
  }

  public synchronized void stopIfRunning() {
    if (!isCanceled()) stop();
  }
}
