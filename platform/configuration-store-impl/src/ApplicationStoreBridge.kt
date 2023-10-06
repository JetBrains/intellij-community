// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMs
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentWriter
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import io.opentelemetry.api.metrics.Meter
import org.jdom.Element
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

internal class AppStorageContentReader : JpsFileContentReader {
  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    val start = System.currentTimeMillis()

    val filePath = JpsPathUtil.urlToPath(fileUrl)
    val element: Element? = if (isApplicationLevelFile(filePath)) {
      val storageSpec = FileStorageAnnotation(PathUtil.getFileName(filePath), false, StateSplitterEx::class.java)

      @Suppress("UNCHECKED_CAST")
      val storage = ApplicationManager.getApplication().stateStore.storageManager.getStateStorage(storageSpec) as StateStorageBase<StateMap>
      val stateMap = storage.getStorageData()
      stateMap.getElement(componentName)
    }
    else {
      null
    }
    loadComponentTimeMs.addElapsedTimeMs(start)
    return element
  }

  override fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap {
    return PathMacroManager.getInstance(ApplicationManager.getApplication()).expandMacroMap
  }

  private fun isApplicationLevelFile(filePath: String): Boolean {
    return FileUtil.isAncestor(Path.of(PathManager.getOptionsPath()), Path.of(filePath), false)
  }

  companion object {
    private val loadComponentTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadComponentTimeGauge = meter.gaugeBuilder("jps.app.storage.content.reader.load.component.ms")
        .ofLongs().buildObserver()

      meter.batchCallback({ loadComponentTimeGauge.record(loadComponentTimeMs.get()) }, loadComponentTimeGauge)
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}

internal class AppStorageContentWriter(private val session: SaveSessionProducerManager) : JpsFileContentWriter {
  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    val start = System.currentTimeMillis()

    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (isApplicationLevelFile(filePath)) {
      val storageSpec = FileStorageAnnotation(PathUtil.getFileName(filePath), false, StateSplitterEx::class.java)

      @Suppress("UNCHECKED_CAST")
      val storage = ApplicationManager.getApplication().stateStore.storageManager.getStateStorage(storageSpec) as StateStorageBase<StateMap>
      session.getProducer(storage)?.setState(null, componentName, componentTag)
    }

    saveComponentTimeMs.addElapsedTimeMs(start)
  }

  override fun getReplacePathMacroMap(fileUrl: String): PathMacroMap {
    return PathMacroManager.getInstance(ApplicationManager.getApplication()).replacePathMap
  }

  private fun isApplicationLevelFile(filePath: String): Boolean {
    return FileUtil.isAncestor(Path.of(PathManager.getOptionsPath()), Path.of(filePath), false)
  }

  companion object {
    private val saveComponentTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val saveComponentTimeGauge = meter.gaugeBuilder("jps.app.storage.content.writer.save.component.ms")
        .ofLongs().buildObserver()

      meter.batchCallback({ saveComponentTimeGauge.record(saveComponentTimeMs.get()) }, saveComponentTimeGauge)
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}

