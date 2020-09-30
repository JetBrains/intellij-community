// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import kotlin.math.max

private class MemorySizeConfigurator : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) return

    val memoryAdjusted = PropertiesComponent.getInstance().isTrueValue("ide.memory.adjusted")
    if (memoryAdjusted) return

    val currentXmx = max(VMOptions.readOption(VMOptions.MemoryKind.HEAP, true),
                         VMOptions.readOption(VMOptions.MemoryKind.HEAP, false))
    if (currentXmx < 0) {
      // Don't know how much -Xmx we have
      LOG.info("Memory size configurator skipped: Unable to determine current -Xmx. VM options file is ${System.getProperty("jb.vmOptionsFile")}")
      return
    }
    if (currentXmx > 750) {
      // Memory has already been adjusted by the user manually
      return
    }

    val osMxBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    val totalPhysicalMemory = osMxBean.totalPhysicalMemorySize shr 20

    val newXmx = MemorySizeConfiguratorService.getInstance().getSuggestedMemorySize(currentXmx, totalPhysicalMemory.toInt())

    val currentXms = max(VMOptions.readOption(VMOptions.MemoryKind.MIN_HEAP, true),
                         VMOptions.readOption(VMOptions.MemoryKind.MIN_HEAP, false))

    if (currentXms < 0 || newXmx < currentXms) {
      LOG.info("Memory size configurator skipped: avoiding invalid configuration with -Xmx ${currentXms} and -Xmx ${newXmx}")
    }
    else {
      VMOptions.writeOption(VMOptions.MemoryKind.HEAP, newXmx)
      LOG.info("Physical memory ${totalPhysicalMemory}M, minimum memory size ${currentXms}M, -Xmx adjusted from ${currentXmx}M to ${newXmx}M")
    }
    PropertiesComponent.getInstance().setValue("ide.memory.adjusted", true)
  }

  companion object {
    val LOG = Logger.getInstance(MemorySizeConfigurator::class.java)
  }
}

// Allow overriding in other IDEs
open class MemorySizeConfiguratorService {
  companion object {
    fun getInstance(): MemorySizeConfiguratorService = service()
  }

  open fun getSuggestedMemorySize(currentXmx: Int, totalPhysicalMemory: Int): Int {
    return (totalPhysicalMemory / 8).coerceIn(750, 2048)
  }
}
