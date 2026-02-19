// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.Java11Shim
import com.intellij.util.system.CpuArch
import org.jetbrains.annotations.ApiStatus

private const val archPluginAliasIdPrefix = "com.intellij.modules.arch."

@ApiStatus.Internal
enum class PluginCpuArchRequirement {
  X86 {
    override fun isHostArch(): Boolean = CpuArch.CURRENT == CpuArch.X86
  },
  X86_64 {
    override fun isHostArch(): Boolean = CpuArch.CURRENT == CpuArch.X86_64
  },
  ARM32 {
    override fun isHostArch(): Boolean = CpuArch.CURRENT == CpuArch.ARM32
  },
  ARM64 {
    override fun isHostArch(): Boolean = CpuArch.CURRENT == CpuArch.ARM64
  },
  Unknown {
    override fun isHostArch(): Boolean = false
  };

  val pluginAliasId: PluginId = PluginId.getId(archPluginAliasIdPrefix + name.lowercase())

  abstract fun isHostArch(): Boolean

  companion object {
    private val directory = HashMap<PluginId, PluginCpuArchRequirement>().let { map ->
      entries.associateByTo(map) { it.pluginAliasId }
      Java11Shim.INSTANCE.copyOf(map)
    }

    fun getHostCpuArchModuleIds(): List<PluginId> {
      return entries.mapNotNull { it.takeIf { it.isHostArch() }?.pluginAliasId }
    }

    fun fromPluginId(pluginId: PluginId): PluginCpuArchRequirement? {
      return directory[pluginId] ?: Unknown.takeIf { looksLikeCpuArchModuleId(pluginId.idString) }
    }

    private fun looksLikeCpuArchModuleId(idString: String): Boolean = idString.startsWith(archPluginAliasIdPrefix)
  }
}
