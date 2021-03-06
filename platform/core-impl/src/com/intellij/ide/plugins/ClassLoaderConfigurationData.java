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
  static final Set<PluginId> SEPARATE_CLASSLOADER_FOR_SUB_ONLY;
  static final Set<PluginId> SEPARATE_CLASSLOADER_FOR_SUB_EXCLUDE;

  static {
    String value = System.getProperty("idea.classloader.per.descriptor.only");
    if (value == null) {
      SEPARATE_CLASSLOADER_FOR_SUB_ONLY = new ReferenceOpenHashSet<>(new PluginId[]{
        PluginId.getId("org.jetbrains.plugins.ruby"),
        PluginId.getId("com.jetbrains.rubymine.customization"),
        PluginId.getId("JavaScript"),
        PluginId.getId("Docker"),
        PluginId.getId("com.intellij.diagram"),
      });
    }
    else if (value.isEmpty()) {
      SEPARATE_CLASSLOADER_FOR_SUB_ONLY = Collections.emptySet();
    }
    else {
      SEPARATE_CLASSLOADER_FOR_SUB_ONLY = new ReferenceOpenHashSet<>();
      for (String id : value.split(",")) {
        SEPARATE_CLASSLOADER_FOR_SUB_ONLY.add(PluginId.getId(id));
      }
    }

    SEPARATE_CLASSLOADER_FOR_SUB_EXCLUDE = new ReferenceOpenHashSet<>(new PluginId[]{
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
      PluginId.getId("com.jetbrains.space"),
      PluginId.getId("com.intellij.spring"),
    });
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
