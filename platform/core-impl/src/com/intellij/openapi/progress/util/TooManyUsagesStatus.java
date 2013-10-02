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
  private static final Null NULL = new Null();

  @NotNull
  public static TooManyUsagesStatus getFrom(@Nullable ProgressIndicator indicator) {
    TooManyUsagesStatus data = null;
    if (indicator instanceof UserDataHolder) {
      data = ((UserDataHolder)indicator).getUserData(KEY);
    }
    if (data == null) data = NULL;
    return data;
  }
  public static TooManyUsagesStatus createFor(@NotNull ProgressIndicator indicator) {
    TooManyUsagesStatus data = null;
    if (indicator instanceof UserDataHolder) {
      data = new TooManyUsagesStatus();
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

  private final AtomicReference<Status> tooManyUsagesStatus = new AtomicReference<Status>(Status.FEW_USAGES);
  private final CountDownLatch waitWhileUserClick = new CountDownLatch(1);

  public void pauseProcessingIfTooManyUsages() {
    if (tooManyUsagesStatus.get() == Status.WARNING_DIALOG_SHOWN) {
      //assert ApplicationManager.getApplication().isDispatchThread() || !ApplicationManager.getApplication().isReadAccessAllowed();
      try {
        waitWhileUserClick.await(1, TimeUnit.SECONDS);
      }
      catch (InterruptedException ignored) {
      }
    }
  }

  private static class Null extends TooManyUsagesStatus {
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
