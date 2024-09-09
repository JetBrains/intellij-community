// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This interface is used to modify a list of settings
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface ConfigurablesPatcher {

  ExtensionPointName<ConfigurablesPatcher> EP_NAME = ExtensionPointName.create("com.intellij.configurablesPatcher");

  /**
   * Modifies a list of settings
   *
   * @param originalConfigurables - the list of settings
   * @param project - a project used to load project settings for or {@code null}
   */
  void modifyOriginalConfigurablesList(@NotNull List<Configurable> originalConfigurables,
                                       @Nullable Project project);
}
