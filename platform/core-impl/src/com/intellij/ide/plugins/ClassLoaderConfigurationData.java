// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public final class ClassLoaderConfigurationData {
  static final boolean SEPARATE_CLASSLOADER_FOR_SUB = Boolean.parseBoolean(System.getProperty("idea.classloader.per.descriptor", "true"));
  private static final Set<PluginId> SEPARATE_CLASSLOADER_FOR_SUB_ONLY = new ReferenceOpenHashSet<>();
  private static final Set<PluginId> SEPARATE_CLASSLOADER_FOR_SUB_EXCLUDE = ReferenceOpenHashSet.of(
    PluginId.getId("org.jetbrains.kotlin"),
    PluginId.getId("com.intellij.java"),
    PluginId.getId("com.intellij.spring.batch"),
    PluginId.getId("com.intellij.spring.integration"),
    PluginId.getId("com.intellij.spring.messaging"),
    PluginId.getId("com.intellij.spring.ws"),
    PluginId.getId("com.intellij.spring.websocket"),
    PluginId.getId("com.intellij.spring.webflow"),
    PluginId.getId("com.intellij.spring.security"),
    PluginId.getId("com.intellij.spring.osgi"),
    PluginId.getId("com.intellij.spring.mvc"),
    PluginId.getId("com.intellij.spring.data"),
    PluginId.getId("com.intellij.spring.boot.run.tests"),
    PluginId.getId("com.intellij.spring.boot"),
    PluginId.getId("com.intellij.spring")
  );

  static {
    String property = System.getProperty("idea.classloader.per.descriptor.only");
    String[] pluginIds = property == null ?
                         new String[]{
                           "org.jetbrains.plugins.ruby",
                           "PythonCore",
                           "com.jetbrains.rubymine.customization",
                           "JavaScript",
                           "Docker",
                           "com.intellij.diagram"
                         } :
                         property.split(",");

    for (String idString : pluginIds) {
      SEPARATE_CLASSLOADER_FOR_SUB_ONLY.add(PluginId.getId(idString));
    }
  }

  public static boolean isClassloaderPerDescriptorEnabled(@NotNull PluginId pluginId, @Nullable String packagePrefix) {
    if (!SEPARATE_CLASSLOADER_FOR_SUB || SEPARATE_CLASSLOADER_FOR_SUB_EXCLUDE.contains(pluginId)) {
      return false;
    }
    return packagePrefix != null ||
           SEPARATE_CLASSLOADER_FOR_SUB_ONLY.isEmpty() ||
           SEPARATE_CLASSLOADER_FOR_SUB_ONLY.contains(pluginId);
  }
}
