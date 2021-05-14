// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection.general;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleInjection implements Injection {

  private final String injectedId;
  private final String prefix;
  private final String suffix;
  private final String supportId;

  public SimpleInjection(@NotNull String injectedId, @NotNull String prefix, @NotNull String suffix, @Nullable String supportId) {
    this.injectedId = injectedId;
    this.prefix = prefix;
    this.suffix = suffix;
    this.supportId = supportId;
  }

  @Override
  public @NotNull @NlsSafe String getInjectedLanguageId() {
    return injectedId;
  }

  @Override
  public @NotNull String getPrefix() {
    return prefix;
  }

  @Override
  public @NotNull String getSuffix() {
    return suffix;
  }

  @Override
  public @Nullable @NlsSafe String getSupportId() {
    return supportId;
  }
}
