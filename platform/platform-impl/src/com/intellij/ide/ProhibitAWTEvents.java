// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Function;

/**
 * Use to assert that no AWT events are pumped during some activity (e.g., action update, write operations, etc.)
 */
@ApiStatus.Internal
public final class ProhibitAWTEvents implements IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance(ProhibitAWTEvents.class);

  private final String myActivityName;
  private final Function<? super AWTEvent, String> myErrorFunction;
  private boolean myReported;

  private ProhibitAWTEvents(@NotNull String activityName, @Nullable Function<? super AWTEvent, String> errorFunction) {
    myActivityName = activityName;
    myErrorFunction = errorFunction;
  }

  @Override
  public boolean dispatch(@NotNull AWTEvent e) {
    String message = myReported ? null :
                     myErrorFunction == null ? "AWT events are prohibited inside " + myActivityName + "; got " + e :
                     myErrorFunction.apply(e);
    if (message != null) {
      myReported = true;
      LOG.error(message);
    }
    return true;
  }

  public static @NotNull AccessToken start(@NotNull @NonNls String activityName) {
    return doStart(activityName, null);
  }

  public static @NotNull AccessToken startFiltered(@NotNull @NonNls String activityName, @NotNull Function<? super AWTEvent, String> errorFunction) {
    return doStart(activityName, errorFunction);
  }

  private static @NotNull AccessToken doStart(@NotNull @NonNls String activityName, @Nullable Function<? super AWTEvent, String> errorFunction) {
    if (!EDT.isCurrentThreadEdt()) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    ProhibitAWTEvents dispatcher = new ProhibitAWTEvents(activityName, errorFunction);
    IdeEventQueue.getInstance().addPostprocessor(dispatcher, (Disposable)null);
    return new AccessToken() {
      @Override
      public void finish() {
        IdeEventQueue.getInstance().removePostprocessor(dispatcher);
      }
    };
  }
}
