// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Java11Shim
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus

private const val osModuleIdPrefix = "com.intellij.modules.os."

@ApiStatus.Experimental
enum class IdeaPluginOsRequirement {
  Unknown {
    override fun isHostOs(): Boolean = false
  },
  Windows {
    override fun isHostOs(): Boolean = OS.CURRENT == OS.Windows
  },
  Mac {
    override fun isHostOs(): Boolean = OS.CURRENT == OS.macOS
    override fun toString(): String = "macOS"
  },
  Linux {
    override fun isHostOs(): Boolean = OS.CURRENT == OS.Linux
  },
  FreeBSD {
    override fun isHostOs(): Boolean = OS.CURRENT == OS.FreeBSD
  },
  Unix {
    override fun isHostOs(): Boolean = OS.CURRENT != OS.Windows
  },
  XWindow {
    override fun isHostOs(): Boolean = OS.isGenericUnix()
  };

  val moduleId: PluginId = PluginId.getId(osModuleIdPrefix + name.lowercase())

  abstract fun isHostOs(): Boolean

  companion object {
    private val directory = HashMap<PluginId, IdeaPluginOsRequirement>().let { map ->
      entries.associateByTo(map) { it.moduleId }
      Java11Shim.INSTANCE.copyOf(map)
    }

    fun getHostOsModuleIds(): List<PluginId> = entries.mapNotNull { it.takeIf { it.isHostOs() }?.moduleId }

    fun fromModuleId(moduleId: PluginId): IdeaPluginOsRequirement? =
      directory[moduleId] ?: Unknown.takeIf { looksLikeOsModuleId(moduleId.idString) }

    private fun looksLikeOsModuleId(idString: String): Boolean = idString.startsWith(osModuleIdPrefix)
  }
}
