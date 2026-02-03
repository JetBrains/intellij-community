// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class CompletionContributorEP extends LanguageExtensionPoint<CompletionContributor> {
  /**
   * @deprecated {@link #CompletionContributorEP(String, String, PluginDescriptor)} must be used to ensure that plugin descriptor is set.
   */
  @Deprecated(forRemoval = true)
  public CompletionContributorEP() {
  }

  @TestOnly
  @NonInjectable
  public CompletionContributorEP(@NotNull String language,
                                 @NotNull String implementationClass,
                                 @NotNull PluginDescriptor pluginDescriptor) {
    super(language, implementationClass, pluginDescriptor);
  }
}
