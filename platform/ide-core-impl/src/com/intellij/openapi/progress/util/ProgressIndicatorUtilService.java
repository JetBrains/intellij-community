// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.WriteLockReacquisitionListener;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.isWriteActionRunningOrPending;

@Service(Service.Level.APP)
final class ProgressIndicatorUtilService implements Disposable {

  private final Logger LOG = Logger.getInstance(ProgressIndicatorUtilService.class);

  static @NotNull ProgressIndicatorUtilService getInstance(@NotNull Application application) {
    return application.getService(ProgressIndicatorUtilService.class);
  }

  private final @NotNull ApplicationEx myApplication;
  private final List<Runnable> myWriteActionCancellations = ContainerUtil.createLockFreeCopyOnWriteList();

  private ProgressIndicatorUtilService() {
    myApplication = ApplicationManagerEx.getApplicationEx();
    myApplication.addApplicationListener(new ApplicationListener() {
      @Override
      public void beforeWriteActionStart(@NotNull Object action) {
        cancelActionsToBeCancelledBeforeWrite();
      }
    }, this);
    myApplication.addSuspendingWriteActionListener(new WriteLockReacquisitionListener() {
      @Override
      public void beforeWriteLockReacquired() {
        cancelActionsToBeCancelledBeforeWrite();
      }
    }, this);
  }

  @Override
  public void dispose() {
    if (!myWriteActionCancellations.isEmpty()) {
      throw new AssertionError("Cancellations are not empty! " + myWriteActionCancellations);
    }
  }

  private void cancelActionsToBeCancelledBeforeWrite() {
    for (Runnable cancellation : myWriteActionCancellations) {
      cancellation.run();
    }
  }

  boolean runActionAndCancelBeforeWrite(@NotNull Runnable cancellation, @NotNull Runnable action) {
    if (isWriteActionRunningOrPending(myApplication)) {
      cancellation.run();
      return false;
    }

    myWriteActionCancellations.add(cancellation);
    try {
      if (isWriteActionRunningOrPending(myApplication)) {
        // the listener might not be notified if write action was requested concurrently with the listener addition
        cancellation.run();
        return false;
      }
      action.run();
      return true;
    }
    finally {
      myWriteActionCancellations.remove(cancellation);
    }
  }

  private static void throwCannotWriteException() {
    throw new IllegalStateException("Write actions are prohibited");
  }
}
