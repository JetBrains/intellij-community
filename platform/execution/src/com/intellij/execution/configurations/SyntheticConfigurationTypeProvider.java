// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

@ApiStatus.Experimental
public interface SyntheticConfigurationTypeProvider {

  ExtensionPointName<SyntheticConfigurationTypeProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.execution.syntheticConfigurationTypeProvider");

  @NotNull @Unmodifiable
  Collection<? extends ConfigurationType> getConfigurationTypes();
}
