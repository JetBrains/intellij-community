// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.PathMacroMap
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.serialization.impl.JpsAppFileContentWriter
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import com.intellij.util.PathUtilRt
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import io.opentelemetry.api.metrics.Meter
import org.jdom.Element
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

internal class AppStorageContentReader : JpsFileContentReader {
  override fun loadComponent(
    fileUrl: String,
    componentName: String,
    customModuleFilePath: String?,
  ): Element? {
    return loadComponentTimeMs.addMeasuredTime {
      val filePath = JpsPathUtil.urlToPath(fileUrl)
      val element: Element? = if (isApplicationLevelFile(filePath)) {
        val storageSpec = FileStorageAnnotation(PathUtilRt.getFileName(filePath), false, StateSplitterEx::class.java)
        @Suppress("UNCHECKED_CAST")
        (ApplicationManager.getApplication().stateStore.storageManager.getStateStorage(storageSpec) as StateStorageBase<StateMap>)
          .getStorageData()
          .getElement(componentName)
      }
      else {
        null
      }
      return@addMeasuredTime element
    }
  }

  override fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap {
    return PathMacroManager.getInstance(ApplicationManager.getApplication()).expandMacroMap
  }

  private fun isApplicationLevelFile(filePath: String): Boolean = Path.of(filePath).startsWith(Path.of(PathManager.getOptionsPath()))

  companion object {
    private val loadComponentTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadComponentTimeCounter = meter.counterBuilder("jps.app.storage.content.reader.load.component.ms").buildObserver()
      meter.batchCallback({ loadComponentTimeCounter.record(loadComponentTimeMs.asMilliseconds()) }, loadComponentTimeCounter)
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}

internal class AppStorageContentWriter(private val session: SaveSessionProducerManager) : JpsAppFileContentWriter {
  companion object {
    private val saveComponentTimeMs = MillisecondsMeasurer()

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val saveComponentTimeCounter = meter.counterBuilder("jps.app.storage.content.writer.save.component.ms").buildObserver()
      meter.batchCallback({ saveComponentTimeCounter.record(saveComponentTimeMs.asMilliseconds()) }, saveComponentTimeCounter)
    }
  }

  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    saveComponentTimeMs.addMeasuredTime {
      val filePath = JpsPathUtil.urlToPath(fileUrl)
      if (isApplicationLevelFile(filePath)) {
        val storageSpec = FileStorageAnnotation(PathUtilRt.getFileName(filePath), false, StateSplitterEx::class.java)

        @Suppress("UNCHECKED_CAST")
        val storage = ApplicationManager.getApplication().stateStore.storageManager.getStateStorage(storageSpec) as StateStorageBase<StateMap>
        session.getProducer(storage)?.setState(
          component = null,
          componentName = componentName,
          // doesn't matter for now
          pluginId = PluginManagerCore.CORE_ID,
          state = componentTag,
        )
      }
    }
  }

  override fun getReplacePathMacroMap(fileUrl: String): PathMacroMap {
    return PathMacroManager.getInstance(ApplicationManager.getApplication()).replacePathMap
  }

  override suspend fun saveSession() {
    session.save(SaveResult())
  }

  private fun isApplicationLevelFile(filePath: String): Boolean = Path.of(filePath).startsWith(Path.of(PathManager.getOptionsPath()))
}
