// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.Java11Shim
import org.jetbrains.annotations.ApiStatus

private const val moduleNamePrefix = "com.intellij.platform."

@ApiStatus.Experimental
enum class IdeaPluginPlatform {
  Unknown {
    override fun isHostPlatform(): Boolean = false
  },
  Windows {
    override fun isHostPlatform(): Boolean = SystemInfoRt.isWindows
  },
  Mac {
    override fun isHostPlatform(): Boolean = SystemInfoRt.isMac
    override fun toString(): String = "macOS"
  },
  Linux {
    override fun isHostPlatform(): Boolean = SystemInfoRt.isLinux
  },
  FreeBSD {
    override fun isHostPlatform(): Boolean = SystemInfoRt.isFreeBSD
  },
  Solaris {
    override fun isHostPlatform(): Boolean = SystemInfoRt.isSolaris
  },
  Unix {
    override fun isHostPlatform(): Boolean = SystemInfoRt.isUnix
  },
  XWindow {
    override fun isHostPlatform(): Boolean = SystemInfoRt.isXWindow
  };

  val moduleId: PluginId = PluginId.getId(moduleNamePrefix + name.lowercase())

  abstract fun isHostPlatform(): Boolean

  companion object {
    private val directory = HashMap<PluginId, IdeaPluginPlatform>().let { map ->
      entries.associateByTo(map) { it.moduleId }
      Java11Shim.INSTANCE.copyOf(map)
    }

    fun getHostPlatformModuleIds(): List<PluginId> = entries.mapNotNull { it.takeIf { it.isHostPlatform() }?.moduleId }

    fun fromModuleId(moduleId: PluginId): IdeaPluginPlatform? {
      return directory.get(moduleId) ?: Unknown.takeIf { looksLikePlatformId(moduleId.idString) }
    }

    private fun looksLikePlatformId(idString: String): Boolean {
      return idString.startsWith(moduleNamePrefix) && idString != "com.intellij.platform.images"
    }
  }
}