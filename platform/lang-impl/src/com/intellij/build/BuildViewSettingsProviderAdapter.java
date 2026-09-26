// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This adapter is required to prevent a console-view instance (and it's internals) from being leaked.
 * There are several scenarios in which an instance of console-view is used as a BuildViewSettingsProvider.
 * By moving settings values out of console-view based BuildViewSettingsProvider we could prevent a leak.
 */
@ApiStatus.Internal
public final class BuildViewSettingsProviderAdapter implements BuildViewSettingsProvider {

  private final boolean myExecutionViewHidden;
  private final boolean myIsSingleConsoleView;

  public BuildViewSettingsProviderAdapter(@NotNull BuildViewSettingsProvider buildViewSettingsProvider) {
    myExecutionViewHidden = buildViewSettingsProvider.isExecutionViewHidden();
    myIsSingleConsoleView = buildViewSettingsProvider.isSingleBuildConsoleView();
  }

  @Override
  public boolean isExecutionViewHidden() {
    return myExecutionViewHidden;
  }

  @Override
  public boolean isSingleBuildConsoleView() {
    return myIsSingleConsoleView;
  }
}
