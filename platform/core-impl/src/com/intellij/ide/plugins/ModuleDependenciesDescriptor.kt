// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId

internal class ModuleDependenciesDescriptor(@JvmField val modules: List<ModuleItem>, @JvmField val plugins: List<PluginItem>) {
  companion object {
    @JvmField
    val EMPTY = ModuleDependenciesDescriptor(emptyList(), emptyList())
  }

  fun findModuleByName(name: String): ModuleItem? {
    for (module in modules) {
      if (module.name == name) {
        return module
      }
    }
    return null
  }

  internal class ModuleItem(@JvmField val name: String, @JvmField val packageName: String?) {
    init {
      if (packageName != null && packageName.endsWith(".")) {
        throw RuntimeException("packageName must not ends with dot: $packageName")
      }
    }

    override fun toString(): String {
      return "ModuleItem(name=$name, packageName=$packageName)"
    }
  }

  internal class PluginItem(@JvmField val id: PluginId) {
    override fun toString(): String {
      return "PluginItem(id=$id)"
    }
  }

  override fun toString(): String {
    return "ModuleDependenciesDescriptor(modules=$modules, plugins=$plugins)"
  }
}

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

  override fun toString(): String {
    return "PluginContentDescriptor(modules=$modules)"
  }
}