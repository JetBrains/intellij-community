// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

/**
 * Use to assert that no AWT events are pumped during some activity (e.g. action update, write operations, etc)
 */
public final class ProhibitAWTEvents implements IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance(ProhibitAWTEvents.class);

  private static long ourUseCount;

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

  @NotNull
  public static AccessToken start(@NotNull @NonNls String activityName) {
    return doStart(activityName, null);
  }

  @NotNull
  public static AccessToken startFiltered(@NotNull @NonNls String activityName, @NotNull Function<? super AWTEvent, String> errorFunction) {
    return doStart(activityName, errorFunction);
  }

  @NotNull
  private static AccessToken doStart(@NotNull @NonNls String activityName, @Nullable Function<? super AWTEvent, String> errorFunction) {
    if (!EDT.isCurrentThreadEdt()) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    ProhibitAWTEvents dispatcher = new ProhibitAWTEvents(activityName, errorFunction);
    IdeEventQueue.getInstance().addPostprocessor(dispatcher, null);
    ourUseCount ++;
    return new AccessToken() {
      @Override
      public void finish() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourUseCount--;
        IdeEventQueue.getInstance().removePostprocessor(dispatcher);
      }
    };
  }

  public static boolean areEventsProhibited() {
    if (!SwingUtilities.isEventDispatchThread()) {
      return false;
    }
    return ourUseCount != 0;
  }
}
