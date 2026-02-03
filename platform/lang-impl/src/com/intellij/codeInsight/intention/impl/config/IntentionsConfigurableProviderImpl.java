// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IntentionsConfigurableProviderImpl extends IntentionsConfigurableProvider {
  @Override
  public @Nullable IntentionsConfigurable createConfigurable() {
    return new IntentionSettingsConfigurable();
  }
}
