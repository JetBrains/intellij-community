// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Provides multiple {@link LanguageCodeStyleSettingsProvider} instances initialized programmatically.
 */
@ApiStatus.Internal
public interface LanguageCodeStyleSettingsContributor {
  ExtensionPointName<LanguageCodeStyleSettingsContributor> EP_NAME =
    ExtensionPointName.create("com.intellij.langCodeStyleSettingsContributor");

  List<LanguageCodeStyleSettingsProvider> getProviders();
}
