// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import java.io.IOException
import java.nio.file.Path

@ApiStatus.Internal
interface OneToManyPathMapping {
  @Throws(IOException::class)
  fun getOutputs(path: String): Collection<String>?

  @Throws(IOException::class)
  fun getOutputs(file: Path): Collection<Path>?

  @Throws(IOException::class)
  fun setOutputs(path: String, outPaths: List<String>)

  @Throws(IOException::class)
  fun remove(path: String)
}

@ApiStatus.Internal
interface SourceToOutputMappingCursor : Iterator<String> {
  /** [next] must be called beforehand */
  val outputPaths: Array<String>
}

@ApiStatus.Internal
interface OutputToTargetMapping {
  @Throws(IOException::class)
  fun removeTargetAndGetSafeToDeleteOutputs(
    outputPaths: Collection<String>,
    currentTargetId: Int,
    srcToOut: SourceToOutputMapping,
  ): Collection<String>

  @Throws(IOException::class)
  fun removeMappings(outputPaths: Collection<String>, buildTargetId: Int, srcToOut: SourceToOutputMapping)
}