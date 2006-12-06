package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.progress.util.ProgressIndicatorBase;

/**
 * @author cdr
 */
public class DaemonProgressIndicator extends ProgressIndicatorBase {
  public void stop() {
    super.stop();
    cancel();
  }
}
