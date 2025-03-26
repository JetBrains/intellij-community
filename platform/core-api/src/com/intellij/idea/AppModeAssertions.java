// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public final class AppModeAssertions {

  private static final class Holder {
    private static final Logger LOG = Logger.getInstance(AppModeAssertions.class);
  }

  private AppModeAssertions() { }

  public static boolean checkFrontend() {
    return !AppMode.isRemoteDevHost(); // don't report in frontend and monolith
  }

  public static void assertFrontend(boolean hard) {
    if (checkFrontend()) {
      return;
    }
    Error e = new AppModeAssertionError("frontend");
    if (hard) {
      throw e;
    }
    else {
      Holder.LOG.error(e);
    }
  }

  public static boolean checkBackend() {
    return !PlatformUtils.isJetBrainsClient(); // don't report in backend and monolith
  }

  public static void assertBackend(boolean hard) {
    if (checkBackend()) {
      return;
    }
    Error e = new AppModeAssertionError("backend");
    if (hard) {
      throw e;
    }
    else {
      Holder.LOG.error(e);
    }
  }

  static final class AppModeAssertionError extends AssertionError {

    AppModeAssertionError(@NotNull String expectedMode) {
      super("The operations is allowed only in " + expectedMode);
    }
  }
}
