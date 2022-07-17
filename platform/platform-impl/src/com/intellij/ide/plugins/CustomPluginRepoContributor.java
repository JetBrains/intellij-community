// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Lets plugins add their own custom plugin repos without the need to persistently modify {@link UpdateSettings}
 */
public interface CustomPluginRepoContributor {

  ExtensionPointName<CustomPluginRepoContributor> EP_NAME = ExtensionPointName.create("com.intellij.customPluginRepoContributor");

  @NotNull
  List<String> getRepoUrls();

}
