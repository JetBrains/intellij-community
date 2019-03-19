// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.script;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Provides plugin name and it's scripts directory
 */
@ApiStatus.Experimental
public interface ScriptedExtensionsProvider {
  ExtensionPointName<ScriptedExtensionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.scriptedExtensionsProvider");

  /**
   *
   * @return user visible name that is shown in corresponding menus
   */
  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull String getName();

  /**
   *
   * @return directory where bundled resources were extracted to
   */
  @Nullable File getScriptsDirectory();

  /**
   *
   * @return actions to run scripts found inside {@link ScriptedExtensionsProvider#getScriptsDirectory()}
   */
  @NotNull AnAction[] getActions();
}
