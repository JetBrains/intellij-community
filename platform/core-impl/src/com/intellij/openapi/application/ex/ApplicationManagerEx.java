// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;

public final class ApplicationManagerEx extends ApplicationManager {
  public static ApplicationEx getApplicationEx() {
    return (ApplicationEx)ourApplication;
  }

  /**
   * @deprecated Use {@code LoadingState.COMPONENTS_LOADED.isOccurred()}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isAppLoaded() {
    return LoadingState.COMPONENTS_LOADED.isOccurred();
  }
}