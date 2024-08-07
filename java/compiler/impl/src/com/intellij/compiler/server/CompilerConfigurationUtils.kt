// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server

import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import kotlin.math.roundToInt


object CompilerConfigurationUtils {
  private const val MINIMUM_NUMBER_OF_CORES = 4
  private const val MINIMUM_RAM_SIZE_IN_GIB = 8

  /**
   * Checks if it is possible to compile in parallel based on the characteristics of the hardware - number of cores and available RAM
   */
  @JvmStatic
  fun isParallelCompilationAllowedWithCurrentSpecs(project: Project): Boolean {
    val numberOfCores = getNumberOfCores(project)
    if (numberOfCores < MINIMUM_NUMBER_OF_CORES) return false

    val ramSizeInGiB = getRAMSizeInGiB(project) ?: return false
    return ramSizeInGiB >= MINIMUM_RAM_SIZE_IN_GIB
  }

  private fun getNumberOfCores(project: Project): Int = CachedValuesManager
    .getManager(project).getCachedValue(project) {
      CachedValueProvider.Result.create(Runtime.getRuntime().availableProcessors())
    }

  private fun getRAMSizeInGiB(project: Project): Int? = CachedValuesManager
    .getManager(project).getCachedValue(project) {
      CachedValueProvider.Result.create(getRAMSizeInGiBInner())
    }

  private fun getRAMSizeInGiBInner(): Int? {
    return try {
      val bean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean ?: return null
      val ramInBytes = bean.totalMemorySize
      val bytesInGib = (1 shl 30)
      (ramInBytes.toDouble() / bytesInGib).roundToInt()
    }
    catch (_: Exception) {
      null
    }
  }
}