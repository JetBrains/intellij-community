// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

internal class ExperimentalBuildDataManager(
  private val storageManager: StorageManager,
  private val relativizer: PathRelativizerService,
) : BuildDataProvider {
  private val targetToMapManager = ConcurrentHashMap<BuildTarget<*>, PerTargetMapManager>()

  private val typeAwareRelativizer = relativizer.typeAwareRelativizer ?: object : PathTypeAwareRelativizer {
    override fun toRelative(path: String, type: RelativePathType) = relativizer.toRelative(path)

    override fun toRelative(path: Path, type: RelativePathType) = relativizer.toRelative(path)

    override fun toAbsolute(path: String, type: RelativePathType) = relativizer.toFull(path)

    override fun toAbsoluteFile(path: String, type: RelativePathType): Path = Path.of(relativizer.toFull(path))
  }

  /**
   * A map not scoped to a target is problematic because we cannot transfer built target bytecode with JPS build data.
   * Normally, a source file in a module isn't expected to compile into multiple output directories.
   * We can address this later, but for now, let's support it as the old storage did.
   */
  private val outputToTargetMapping = SynchronizedClearableLazy {
    ExperimentalOutputToTargetMapping(storageManager)
  }

  override fun clearCache() {
    storageManager.clearCache()
  }

  override fun removeStaleTarget(targetId: String, targetTypeId: String) {
    storageManager.removeMaps(targetId, targetTypeId)
    outputToTargetMapping.value.removeTarget(targetId, targetTypeId)
  }

  private fun getPerTargetMapManager(target: BuildTarget<*>): PerTargetMapManager {
    return targetToMapManager.computeIfAbsent(target) {
      PerTargetMapManager(
        storageManager = storageManager,
        relativizer = typeAwareRelativizer,
        target = it,
        outputToTargetMapping = outputToTargetMapping,
      )
    }
  }

  override fun getFileStampStorage(target: BuildTarget<*>): StampsStorage<*> {
    return getPerTargetMapManager(target).stamp
  }

  override fun getSourceToOutputMapping(target: BuildTarget<*>): SourceToOutputMapping {
    return getPerTargetMapManager(target).sourceToOutputMapping
  }

  override fun getOutputToTargetMapping(): OutputToTargetMapping = outputToTargetMapping.value

  override fun getSourceToForm(target: BuildTarget<*>): ExperimentalOneToManyPathMapping {
    return getPerTargetMapManager(target).sourceToForm
  }

  override fun closeTargetMaps(target: BuildTarget<*>) {
    targetToMapManager.remove(target)
  }

  override fun removeAllMaps() {
    outputToTargetMapping.drop()
    targetToMapManager.clear()
    storageManager.clean()
  }

  override fun commit() {
    storageManager.commit()
  }

  override fun close() {
    outputToTargetMapping.drop()
    targetToMapManager.clear()
    storageManager.close()
  }
}