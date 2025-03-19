// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class BuildViewSettingsProviderAdapter implements BuildViewSettingsProvider {
  private boolean myExecutionViewHidden;

  public BuildViewSettingsProviderAdapter(BuildViewSettingsProvider buildViewSettingsProvider) {
    myExecutionViewHidden = buildViewSettingsProvider.isExecutionViewHidden();
  }

  @Override
  public boolean isExecutionViewHidden() {
    return myExecutionViewHidden;
  }
}
