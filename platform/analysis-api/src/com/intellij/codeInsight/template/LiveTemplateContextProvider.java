// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public interface LiveTemplateContextProvider {
  @ApiStatus.Internal
  ExtensionPointName<LiveTemplateContextProvider> EP_NAME = new ExtensionPointName<>("com.intellij.liveTemplateContextProvider");

  @NotNull @Unmodifiable
  Collection<LiveTemplateContext> createContexts();
}
