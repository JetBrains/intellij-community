// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.PlatformUtils.PLATFORM_PREFIX_KEY;

@Internal
public final class AppModeAssertions {

  private static final class Holder {
    private static final Logger LOG = Logger.getInstance(AppModeAssertions.class);
  }

  private AppModeAssertions() { }

  public static boolean isFrontend() {
    return PlatformUtils.isJetBrainsClient();
  }

  public static boolean isBackend() {
    return AppMode.isRemoteDevHost();
  }

  public static boolean isMonolith() {
    return !isFrontend() && !isBackend();
  }

  /**
   * Checks if a frontend operation is permitted to be performed in the current product mode.
   *
   * @return `true` in the frontend and monolith mode
   */
  public static boolean checkFrontend() {
    return !isBackend();
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

  /**
   * Checks if a backend operation is permitted to be performed in the current product mode.
   *
   * @return `true` in the backend and monolith mode
   */
  public static boolean checkBackend() {
    return !isFrontend();
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
      super("The operations is allowed only in " + expectedMode +
            "; Platform prefix: " + System.getProperty(PLATFORM_PREFIX_KEY, "not defined") +
            "; getPlatformPrefix: " + PlatformUtils.getPlatformPrefix()
      );
    }
  }
}
