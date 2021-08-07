// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2LongMap
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

@ApiStatus.Internal
interface StartUpPerformanceService {
  companion object {
    @JvmStatic
    fun getInstance(): StartUpPerformanceService {
      @Suppress("UNCHECKED_CAST") val aClass = StartUpPerformanceService::class.java.classLoader
        .loadClass("com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter") as Class<StartUpPerformanceService>
      val ep = ApplicationManager.getApplication().extensionArea
        .getExtensionPoint<Any>("com.intellij.startupActivity") as ExtensionPointImpl<Any>
      return ep.findExtension(aClass, true, ThreeState.YES)!!
    }
  }

  fun reportStatistics(project: Project)

  fun getPluginCostMap(): Map<String, Object2LongMap<String>>

  fun getMetrics(): Object2IntMap<String>?

  fun getLastReport(): ByteBuffer?
}