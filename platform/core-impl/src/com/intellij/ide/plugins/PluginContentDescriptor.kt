// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

internal class PluginContentDescriptor(@JvmField val modules: List<ModuleItem>) {
  companion object {
    @JvmField
    val EMPTY = PluginContentDescriptor(emptyList())
  }

  fun findModuleByName(name: String): ModuleItem? {
    for (module in modules) {
      if (module.name == name) {
        return module
      }
    }
    return null
  }

  internal class ModuleItem(@JvmField val name: String, @JvmField val packageName: String?, @JvmField val configFile: String?) {
    // <module name="intellij.clouds.docker.file" package="com.intellij.docker.dockerFile"/> - xi-include without classloader at all
    @JvmField
    var isInjected = false

    init {
      if (packageName != null && packageName.endsWith(".")) {
        throw RuntimeException("packageName must not ends with dot: $packageName")
      }
    }
  }
}