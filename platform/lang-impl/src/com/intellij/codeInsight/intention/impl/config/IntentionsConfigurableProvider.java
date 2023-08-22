// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.options.ConfigurableProvider;
import org.jetbrains.annotations.Nullable;

public abstract class IntentionsConfigurableProvider extends ConfigurableProvider {
  @Override
  public abstract @Nullable IntentionsConfigurable createConfigurable();
}
