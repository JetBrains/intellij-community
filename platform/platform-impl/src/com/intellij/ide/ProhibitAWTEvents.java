// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/**
 * Use to assert that no AWT events are pumped during some activity (e.g. action update, write operations, etc)
 *
 * @author peter
 */
public final class ProhibitAWTEvents implements IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance(ProhibitAWTEvents.class);

  private static long ourUseCount;

  private final String myActivityName;
  private boolean myReported;

  private ProhibitAWTEvents(@NotNull String activityName) {
    myActivityName = activityName;
  }

  @Override
  public boolean dispatch(@NotNull AWTEvent e) {
    if (!myReported) {
      myReported = true;
      LOG.error("AWT events are prohibited inside " + myActivityName + "; got " + e);
    }
    return true;
  }

  @NotNull
  public static AccessToken start(@NotNull @NonNls String activityName) {
    if (!SwingUtilities.isEventDispatchThread()) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }
    ProhibitAWTEvents dispatcher = new ProhibitAWTEvents(activityName);
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

  public static <T> T prohibitEventsInside(@NonNls @NotNull String activityName, @NotNull Supplier<? extends T> supplier) {
    if (!SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }
    ProhibitAWTEvents dispatcher = new ProhibitAWTEvents(activityName);
    IdeEventQueue.getInstance().addPostprocessor(dispatcher, null);
    try {
      ourUseCount++;
      return supplier.get();
    }
    finally {
      ourUseCount--;
      IdeEventQueue.getInstance().removePostprocessor(dispatcher);
    }
  }

  public static boolean areEventsProhibited() {
    if (!SwingUtilities.isEventDispatchThread()) {
      return false;
    }
    return ourUseCount != 0;
  }
}
