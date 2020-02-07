// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface PathMacroContributor {
  ExtensionPointName<PathMacroContributor> EP_NAME = ExtensionPointName.create("com.intellij.pathMacroContributor");

  void registerPathMacros(@NotNull Map<String, String> macros, @NotNull Map<String, String> legacyMacros);
}
