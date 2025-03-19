// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.passwordSafe.impl;

import com.intellij.util.TimedReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
* @author gregsh
*/
@ApiStatus.Internal
public abstract class PasswordSafeTimed<T> extends TimedReference<T> {
  private int myCheckCount;

  protected PasswordSafeTimed() {
    super(null);
  }

  protected abstract T compute();

  @Override
  public synchronized @NotNull T get() {
    T value = super.get();
    if (value == null) {
      value = compute();
      set(value);
    }
    myCheckCount = 0;
    return value;
  }

  @Override
  protected synchronized boolean checkLocked() {
    int ttlCount = getMinutesToLive() * 60 / SERVICE_DELAY;
    if (ttlCount >= 0 && ++myCheckCount > ttlCount) {
      return super.checkLocked();
    }
    return true;
  }

  protected int getMinutesToLive() {
    return 60;
  }

}
