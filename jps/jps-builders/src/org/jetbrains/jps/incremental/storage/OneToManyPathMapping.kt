// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import org.jetbrains.annotations.ApiStatus
import java.io.IOException

internal interface OneToManyPathMapping : StorageOwner {
  @Throws(IOException::class)
  fun getOutputs(path: String): Collection<String>?

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