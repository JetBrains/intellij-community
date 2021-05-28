// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ApiStatus.Internal
public final class ClassLoaderConfigurationData {
  private ClassLoaderConfigurationData() {
  }

  static final Set<PluginId> SEPARATE_CLASSLOADER_FOR_SUB_EXCLUDE = new ReferenceOpenHashSet<>(new PluginId[]{
    PluginId.getId("org.jetbrains.kotlin"),
    PluginId.getId("com.intellij.java"),
  });

  public static boolean isClassloaderPerDescriptorEnabled(@NotNull PluginId pluginId, @Nullable String packagePrefix) {
    return packagePrefix != null && !SEPARATE_CLASSLOADER_FOR_SUB_EXCLUDE.contains(pluginId);
  }
}
