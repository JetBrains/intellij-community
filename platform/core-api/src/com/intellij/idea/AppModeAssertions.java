// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus;

import static com.intellij.util.PlatformUtils.PLATFORM_PREFIX_KEY;

@ApiStatus.Internal
public final class AppModeAssertions {
  private static final class Holder {
    private static final Logger LOG = Logger.getInstance(AppModeAssertions.class);
  }

  private AppModeAssertions() { }

  public static void assertFrontend(boolean hard) {
    if (!AppMode.isRemoteDevHost()) {
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

  public static void assertBackend(boolean hard) {
    if (!PlatformUtils.isJetBrainsClient()) {
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

  private static final class AppModeAssertionError extends AssertionError {
    private AppModeAssertionError(String expectedMode) {
      super(
        "The operations is allowed only in " + expectedMode +
        "; Platform prefix: " + System.getProperty(PLATFORM_PREFIX_KEY, "not defined") +
        "; getPlatformPrefix: " + PlatformUtils.getPlatformPrefix()
      );
    }
  }
}
