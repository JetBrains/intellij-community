/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TooManyUsagesStatus {
  private static final Key<TooManyUsagesStatus> KEY = Key.create("TooManyUsagesStatus");
  private static final NullStatus NULL_STATUS = new NullStatus();
  private final ProgressIndicator myIndicator;

  private TooManyUsagesStatus(@NotNull ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  @NotNull
  public static TooManyUsagesStatus getFrom(@Nullable ProgressIndicator indicator) {
    TooManyUsagesStatus data = indicator instanceof UserDataHolder ? ((UserDataHolder)indicator).getUserData(KEY) : null;
    return data == null ? NULL_STATUS : data;
  }

  public static TooManyUsagesStatus createFor(@NotNull ProgressIndicator indicator) {
    TooManyUsagesStatus data = null;
    if (indicator instanceof UserDataHolder) {
      data = new TooManyUsagesStatus(indicator);
      ((UserDataHolder)indicator).putUserData(KEY, data);
    }
    return data;
  }

  // return true if dialog needs to be shown
  public boolean switchTooManyUsagesStatus() {
    return tooManyUsagesStatus.get() == Status.FEW_USAGES &&
           tooManyUsagesStatus.compareAndSet(Status.FEW_USAGES, Status.WARNING_DIALOG_SHOWN);
  }

  public void userResponded() {
    waitWhileUserClick.countDown();
    tooManyUsagesStatus.set(Status.USER_RESPONDED);
  }

  public enum Status {
    FEW_USAGES, WARNING_DIALOG_SHOWN, USER_RESPONDED
  }

  private final AtomicReference<Status> tooManyUsagesStatus = new AtomicReference<>(Status.FEW_USAGES);
  private final CountDownLatch waitWhileUserClick = new CountDownLatch(1);

  public void pauseProcessingIfTooManyUsages() {
    if (tooManyUsagesStatus.get() == Status.WARNING_DIALOG_SHOWN) {
      long start = System.currentTimeMillis();
      try {
        while (System.currentTimeMillis() < start + 2000) {
          if (waitWhileUserClick.await(10, TimeUnit.MILLISECONDS)) break;
          if (myIndicator.isCanceled()) break;
        }
      }
      catch (InterruptedException ignored) {
      }
    }
  }

  private static class NullStatus extends TooManyUsagesStatus {
    private NullStatus() {
      super(new EmptyProgressIndicator());
    }

    @Override
    public boolean switchTooManyUsagesStatus() {
      return false;
    }

    @Override
    public void userResponded() {
    }

    @Override
    public void pauseProcessingIfTooManyUsages() {
    }
  }
}
