// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface ConfigurablesPatcher {

  ExtensionPointName<ConfigurablesPatcher> EP_NAME = ExtensionPointName.create("com.intellij.configurablesModificator");

  void modifyOriginalConfigurablesList(@NotNull List<Configurable> originalConfigurables,
                                       @Nullable Project project);
}
