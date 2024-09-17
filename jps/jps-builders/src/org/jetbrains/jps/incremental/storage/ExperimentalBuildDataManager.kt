// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import java.util.concurrent.ConcurrentHashMap

internal class ExperimentalBuildDataManager(
  private val storageManager: StorageManager,
  private val relativizer: PathRelativizerService,
) {
  // only for new experimental storage
  private val targetToMapManager = ConcurrentHashMap<BuildTarget<*>, PerTargetMapManager>()

  /**
   * A map not scoped to a target is problematic because we cannot transfer built target bytecode with JPS build data.
   * Normally, a source file in a module isn't expected to compile into multiple output directories.
   * We can address this later, but for now, let's support it as the old storage did.
   */
  private val outputToTargetMapping = SynchronizedClearableLazy {
    ExperimentalOutputToTargetMapping(storageManager)
  }

  fun clearCache() {
    storageManager.clearCache()
  }

  fun removeStaleTarget(targetId: String, targetTypeId: String) {
    storageManager.removeMaps(targetId, targetTypeId)
    outputToTargetMapping.value.removeTarget(targetId, targetTypeId)
  }

  private fun getPerTargetMapManager(target: BuildTarget<*>): PerTargetMapManager {
    return targetToMapManager.computeIfAbsent(target) {
      PerTargetMapManager(storageManager, relativizer, it, outputToTargetMapping)
    }
  }

  fun getFileStampStorage(target: BuildTarget<*>): StampsStorage<*> {
    return getPerTargetMapManager(target).stamp
  }

  fun getSourceToOutputMapping(target: BuildTarget<*>): SourceToOutputMapping {
    return getPerTargetMapManager(target).sourceToOutputMapping
  }

  fun getOutputToTargetMapping(): OutputToTargetMapping = outputToTargetMapping.value

  fun getSourceToForm(target: BuildTarget<*>): ExperimentalOneToManyPathMapping {
    return getPerTargetMapManager(target).sourceToForm
  }

  fun closeTargetMaps(target: BuildTarget<*>) {
    targetToMapManager.remove(target)
  }

  fun removeAllMaps() {
    outputToTargetMapping.drop()
    targetToMapManager.clear()
    storageManager.clean()
  }

  fun commit() {
    storageManager.commit()
  }

  fun close() {
    outputToTargetMapping.drop()
    targetToMapManager.clear()
    storageManager.close()
  }
}