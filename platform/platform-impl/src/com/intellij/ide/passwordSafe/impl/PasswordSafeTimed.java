/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.passwordSafe.impl;

import com.intellij.util.TimedReference;
import org.jetbrains.annotations.NotNull;

/**
* @author gregsh
*/
public abstract class PasswordSafeTimed<T> extends TimedReference<T> {
  private int myCheckCount;

  protected PasswordSafeTimed() {
    super(null);
  }

  protected abstract T compute();

  @NotNull
  @Override
  public synchronized T get() {
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
