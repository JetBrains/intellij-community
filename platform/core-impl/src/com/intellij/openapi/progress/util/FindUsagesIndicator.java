/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.progress.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class FindUsagesIndicator extends AbstractProgressIndicatorBase {
  public boolean switchTooManyUsagesStatus() {
    return tooManyUsagesStatus.get() == TooManyUsagesStatus.FEW_USAGES &&
           tooManyUsagesStatus.compareAndSet(TooManyUsagesStatus.FEW_USAGES, TooManyUsagesStatus.WARNING_DIALOG_SHOWN);
  }

  public void userResponded() {
    waitWhileUserClick.countDown();
    tooManyUsagesStatus.set(TooManyUsagesStatus.USER_RESPONDED);
  }

  public enum TooManyUsagesStatus {
    FEW_USAGES, WARNING_DIALOG_SHOWN, USER_RESPONDED
  }

  private final AtomicReference<TooManyUsagesStatus> tooManyUsagesStatus = new AtomicReference<TooManyUsagesStatus>(TooManyUsagesStatus.FEW_USAGES);
  private final CountDownLatch waitWhileUserClick = new CountDownLatch(1);

  public void pauseProcessingIfTooManyUsages() {
    if (tooManyUsagesStatus.get() == TooManyUsagesStatus.WARNING_DIALOG_SHOWN) {
      try {
        waitWhileUserClick.await(1, TimeUnit.SECONDS);
      }
      catch (InterruptedException ignored) {
      }
    }
  }
}
