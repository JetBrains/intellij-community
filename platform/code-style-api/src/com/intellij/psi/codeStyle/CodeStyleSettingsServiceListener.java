// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface CodeStyleSettingsServiceListener {
  void fileTypeIndentOptionsFactoryAdded(@NotNull FileTypeIndentOptionsFactory factory);

  void fileTypeIndentOptionsFactoryRemoved(@NotNull FileTypeIndentOptionsFactory factory);

  void languageCodeStyleProviderAdded(@NotNull LanguageCodeStyleProvider provider);

  void languageCodeStyleProviderRemoved(@NotNull LanguageCodeStyleProvider provider);

  void customCodeStyleSettingsFactoryAdded(@NotNull CustomCodeStyleSettingsFactory factory);

  void customCodeStyleSettingsFactoryRemoved(@NotNull CustomCodeStyleSettingsFactory factory);
}