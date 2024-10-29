// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.sun.management.OperatingSystemMXBean
import org.jetbrains.annotations.ApiStatus
import java.lang.management.ManagementFactory
import kotlin.math.max

private const val DEFAULT_XMX = 2048  // must be the same as `VmOptionsGenerator.DEFAULT_XMX`
private const val MAXIMUM_SUGGESTED_XMX = 2 * DEFAULT_XMX

private class MemorySizeConfigurator : ProjectActivity {
  @Suppress("SSBasedInspection")
  private val LOG = Logger.getInstance(MemorySizeConfigurator::class.java)

  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val memoryAdjusted = PropertiesComponent.getInstance().isTrueValue("ide.memory.adjusted")
    if (memoryAdjusted) return

    val currentXmx = max(VMOptions.readOption(VMOptions.MemoryKind.HEAP, true),
                         VMOptions.readOption(VMOptions.MemoryKind.HEAP, false))
    if (currentXmx < 0) {
      // Don't know how much -Xmx we have
      LOG.info("Memory size configurator skipped: Unable to determine current -Xmx. VM options file is ${System.getProperty("jb.vmOptionsFile")}")
      return
    }

    if (currentXmx >= DEFAULT_XMX) {
      // The user has already manually adjusted memory settings
      return
    }

    val osMxBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    val totalPhysicalMemory = osMxBean.totalMemorySize shr 20

    val newXmx = MemorySizeConfiguratorService.getInstance().getSuggestedMemorySize(totalPhysicalMemory.toInt())

    val currentXms = max(VMOptions.readOption(VMOptions.MemoryKind.MIN_HEAP, true),
                         VMOptions.readOption(VMOptions.MemoryKind.MIN_HEAP, false))

    if (currentXms < 0 || newXmx < currentXms) {
      LOG.info("Memory size configurator skipped: avoiding invalid configuration with -Xmx ${currentXms} and -Xmx ${newXmx}")
    }
    else {
      try {
        VMOptions.setOption(VMOptions.MemoryKind.HEAP, newXmx)
        LOG.info("Physical memory ${totalPhysicalMemory}M, minimum memory size ${currentXms}M, -Xmx adjusted from ${currentXmx}M to ${newXmx}M")
      }
      catch (e: Exception) {
        LOG.warn(e)
      }
    }
    PropertiesComponent.getInstance().setValue("ide.memory.adjusted", true)
  }
}

// Allow overriding in other IDEs
@ApiStatus.Internal
open class MemorySizeConfiguratorService {
  companion object {
    fun getInstance(): MemorySizeConfiguratorService = service()
  }

  open fun getSuggestedMemorySize(totalPhysicalMemory: Int): Int =
    if (DEFAULT_XMX > totalPhysicalMemory) 750.coerceAtMost(totalPhysicalMemory) // 750 is the old default
    else (totalPhysicalMemory / 8).coerceIn(DEFAULT_XMX, MAXIMUM_SUGGESTED_XMX)
}
