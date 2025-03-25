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

  public static void softAssertFrontend() {
    if (!AppMode.isRemoteDevHost()) {
      return; // don't report in frontend and monolith
    }
    Holder.LOG.error(new AppModeAssertionError("frontend"));
  }

  public static void softAssertBackend() {
    if (!PlatformUtils.isJetBrainsClient()) {
      return; // don't report in backend and monolith
    }
    Holder.LOG.error(new AppModeAssertionError("backend"));
  }

  static final class AppModeAssertionError extends AssertionError {

    AppModeAssertionError(@NotNull String expectedMode) {
      super("The operations is allowed only in " + expectedMode);
    }
  }
}
