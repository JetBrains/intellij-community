package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.Key;

/**
 * @author cdr
 */
public class DaemonProgressIndicator extends ProgressIndicatorBase implements UserDataHolder {
  private final UserDataHolderBase myUserDataHolder;

  public DaemonProgressIndicator() {
    myUserDataHolder = new UserDataHolderBase();
  }

  public synchronized void stop() {
    super.stop();
    cancel();
  }

  public synchronized void stopIfRunning() {
    if (!isCanceled()) stop();
  }

  public <T> T getUserData(Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    myUserDataHolder.putUserData(key, value);
  }
}
