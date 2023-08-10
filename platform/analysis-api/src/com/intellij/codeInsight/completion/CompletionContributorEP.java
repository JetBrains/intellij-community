// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.NonExtendable
public class CompletionContributorEP extends LanguageExtensionPoint<CompletionContributor> {
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
