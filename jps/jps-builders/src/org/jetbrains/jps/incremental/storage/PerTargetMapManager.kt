// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.storage.dataTypes.LongPairKeyDataType
import org.jetbrains.jps.incremental.storage.dataTypes.StringListDataType
import java.util.function.Supplier

internal class PerTargetMapManager(
  storageManager: StorageManager,
  relativizer: PathTypeAwareRelativizer,
  target: BuildTarget<*>,
  outputToTargetMapping: Supplier<ExperimentalOutputToTargetMapping>,
) {
  @JvmField
  val stamp: StampsStorage<*> = if (ProjectStamps.PORTABLE_CACHES) {
    HashStampStorage.createSourceToStampMap(
      storageManager = storageManager,
      relativizer = relativizer,
      targetId = target.id,
      targetTypeId = target.targetType.typeId,
    )
  }
  else {
    ExperimentalTimeStampStorage.createSourceToStampMap(
      storageManager = storageManager,
      relativizer = relativizer,
      targetId = target.id,
      targetTypeId = target.targetType.typeId,
    )
  }

  val sourceToOutputMapping: SourceToOutputMapping by lazy {
    ExperimentalSourceToOutputMapping.createSourceToOutputMap(
      storageManager = storageManager,
      relativizer = relativizer,
      targetId = target.id,
      targetTypeId = target.targetType.typeId,
      outputToTargetMapping = outputToTargetMapping.get(),
    )
  }

  val sourceToForm: ExperimentalOneToManyPathMapping by lazy {
    ExperimentalOneToManyPathMapping(
      map = storageManager.openMap(
        name = storageManager.getMapName(targetId = target.id, targetTypeId = target.targetType.typeId, suffix = "source-to-form-v1"),
        keyType = LongPairKeyDataType,
        valueType = StringListDataType,
      ),
      relativizer = relativizer,
      keyKind = RelativePathType.SOURCE,
      valueKind = RelativePathType.OUTPUT,
    )
  }
}