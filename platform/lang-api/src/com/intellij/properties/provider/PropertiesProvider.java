// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.properties.provider;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;

public interface PropertiesProvider {
  ExtensionPointName<PropertiesProvider>
    EP_NAME = ExtensionPointName.create("com.intellij.properties.files.provider");

  boolean hasProperty(@NotNull Module classModule, @NotNull String propertyKey, @NotNull String propertyValue, @NotNull GlobalSearchScope scope);
}
