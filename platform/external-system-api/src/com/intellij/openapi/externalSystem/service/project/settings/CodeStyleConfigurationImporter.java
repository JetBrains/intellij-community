/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.service.project.settings;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Experimental
public interface CodeStyleConfigurationImporter<T extends CustomCodeStyleSettings> {
  ExtensionPointName<CodeStyleConfigurationImporter> EP_NAME = ExtensionPointName.create("com.intellij.externalSystem.codeStyleConfigurationImporter");
  void processSettings(@NotNull T settings, @NotNull Map config);
  boolean canImport(@NotNull String langName);

  @NotNull Class<T> getCustomClass();
  @NotNull Language getLanguage();
}