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
  public static AccessToken start(@NotNull String activityName) {
    if (!SwingUtilities.isEventDispatchThread()) {
      // some crazy highlighting queries getData outside EDT: https://youtrack.jetbrains.com/issue/IDEA-162970
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }
    ProhibitAWTEvents dispatcher = new ProhibitAWTEvents(activityName);
    IdeEventQueue.getInstance().addPostprocessor(dispatcher, null);
    return new AccessToken() {
      @Override
      public void finish() {
        IdeEventQueue.getInstance().removePostprocessor(dispatcher);
      }
    };
  }

  public static <T> T prohibitEventsInside(@NonNls @NotNull String activityName, @NotNull Supplier<T> supplier) {
    ProhibitAWTEvents dispatcher = new ProhibitAWTEvents(activityName);
    IdeEventQueue.getInstance().addPostprocessor(dispatcher, null);
    try {
      return supplier.get();
    }
    finally {
      IdeEventQueue.getInstance().removePostprocessor(dispatcher);
    }
  }
}
