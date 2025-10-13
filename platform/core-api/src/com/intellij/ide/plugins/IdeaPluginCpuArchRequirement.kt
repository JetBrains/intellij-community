// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.Java11Shim
import com.intellij.util.system.CpuArch
import org.jetbrains.annotations.ApiStatus

private const val archModuleIdPrefix = "com.intellij.modules.arch."

@ApiStatus.Experimental
enum class IdeaPluginCpuArchRequirement {
  X86 {
    override fun isHostOs(): Boolean = CpuArch.CURRENT == CpuArch.X86
  },
  X86_64 {
    override fun isHostOs(): Boolean = CpuArch.CURRENT == CpuArch.X86_64
  },
  ARM32 {
    override fun isHostOs(): Boolean = CpuArch.CURRENT == CpuArch.ARM32
  },
  ARM64 {
    override fun isHostOs(): Boolean = CpuArch.CURRENT == CpuArch.ARM64
  },
  Unknown {
    override fun isHostOs(): Boolean = false
  };

  val moduleId: PluginId = PluginId.getId(archModuleIdPrefix + name.lowercase())

  abstract fun isHostOs(): Boolean

  companion object {
    private val directory = HashMap<PluginId, IdeaPluginCpuArchRequirement>().let { map ->
      entries.associateByTo(map) { it.moduleId }
      Java11Shim.INSTANCE.copyOf(map)
    }

    fun getHostCpuArchModuleIds(): List<PluginId> {
      return entries.mapNotNull { it.takeIf { it.isHostOs() }?.moduleId }
    }

    fun fromModuleId(moduleId: PluginId): IdeaPluginCpuArchRequirement? {
      return directory[moduleId] ?: Unknown.takeIf { looksLikeCpuArchModuleId(moduleId.idString) }
    }

    private fun looksLikeCpuArchModuleId(idString: String): Boolean = idString.startsWith(archModuleIdPrefix)
  }
}
