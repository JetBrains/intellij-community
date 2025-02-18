// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.storage.dataTypes.LibraryRoots

@ApiStatus.Internal
interface BuildDataProvider {
  fun getSourceToForm(target: BuildTarget<*>): OneToManyPathMapping

  fun getFileStampStorage(target: BuildTarget<*>): StampsStorage<*>

  fun getSourceToOutputMapping(target: BuildTarget<*>): SourceToOutputMapping

  fun getOutputToTargetMapping(): OutputToTargetMapping

  fun removeStaleTarget(targetId: String, targetType: BuildTargetType<*>)

  fun clearCache()

  fun getLibraryRoots(): LibraryRoots

  fun wipeStorage()

  fun flushStorage(memoryCachesOnly: Boolean)

  fun closeTargetMaps(target: BuildTarget<*>)

  fun close()
}