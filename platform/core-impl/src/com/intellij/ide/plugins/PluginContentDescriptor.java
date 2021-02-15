// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class PluginContentDescriptor {
  final List<ModuleItem> modules;

  PluginContentDescriptor(@NotNull List<ModuleItem> modules) {
    this.modules = modules;
  }

  @Nullable ModuleItem findModuleByName(@NotNull String name) {
    for (ModuleItem module : modules) {
      if (module.name.equals(name)) {
        return module;
      }
    }
    return null;
  }

  static final class ModuleItem {
    final String name;
    final String packageName;

    ModuleItem(@NotNull String name, @Nullable String packageName) {
      this.name = name;
      this.packageName = packageName;
      if (packageName != null && packageName.endsWith(".")) {
        throw new RuntimeException("packageName must not ends with dot: " + packageName);
      }
    }
  }
}
