// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a virtual manifest i.e. a manifest that is not stored as the default MANIFEST.MF in sources like manifest descriptions in Maven
 * and Gradle build scripts.
 */
public interface VirtualManifestProvider {
  ExtensionPointName<VirtualManifestProvider> EP_NAME = new ExtensionPointName<>("com.intellij.virtualManifestProvider");

  @Nullable
  @Contract(pure = true)
  static String getAttributeValue(@NotNull Module module, @NotNull String attribute) {
    for (VirtualManifestProvider provider : EP_NAME.getExtensionList()) {
      String value = provider.getValue(module, attribute);
      if (value != null) return value;
    }
    return null;
  }

  @Nullable
  @Contract(pure = true)
  String getValue(@NotNull Module module, @NotNull String attribute);
}
