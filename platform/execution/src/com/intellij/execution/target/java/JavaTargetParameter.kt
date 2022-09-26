// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.target.value.TargetValue
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeoutException

/**
 * Java parameter for run target with requests to unload/download files necessary for its proper functioning
 * (java agents, as instance).
 */
@ApiStatus.Experimental
class JavaTargetParameter private constructor(
  val parameter: TargetValue<String>,
  private val targetPaths: TargetPaths
) {

  /**
   * Resolves all upload and download paths provided by this parameter.
   */
  fun resolvePaths(
    uploadPathsResolver: (TargetPath) -> TargetValue<String>,
    downloadPathsResolver: (TargetPath) -> TargetValue<String>
  ) {
    targetPaths.resolveAll(uploadPathsResolver, downloadPathsResolver)
  }

  /**
   * Returns the parameter with paths resolved to local versions.
   */
  fun toLocalParameter(): String {
    targetPaths.resolveAll(
      uploadPathsResolver = TargetPath::toLocalPath,
      downloadPathsResolver = TargetPath::toLocalPath
    )
    return parameter.targetValue.blockingGet(0)!!
  }

  class Builder(
    private val targetPaths: TargetPaths
  ) {

    constructor(uploadPaths: Set<String> = setOf(),
                downloadPaths: Set<String> = setOf())
      : this(TargetPaths.unordered(uploadPaths, downloadPaths))

    private val parameterBuilderParts: MutableList<() -> String> = mutableListOf()

    /**
     * Adds given string as-is to the overall parameter.
     */
    fun fixed(value: String) = apply { parameterBuilderParts += { value } }

    /**
     * Adds string that is a resolved version of given one. If it is a file path, then it will be resolved to
     * satisfy file system on target.
     */
    fun resolved(value: String) = apply { parameterBuilderParts += { getResolved(value) } }

    fun build(): JavaTargetParameter {
      val parameter = TargetValue.map(TargetValue.EMPTY_VALUE) {
        parameterBuilderParts.joinToString("") { it() }
      }
      return JavaTargetParameter(parameter, targetPaths)
    }

    private fun getResolved(localPath: String): String {
      return try {
        targetPaths.getResolved(localPath).targetValue.blockingGet(0)!!
      }
      catch (e: TimeoutException) {
        throw IllegalArgumentException("Parameter corresponding to $localPath must be resolved", e)
      }
    }
  }

  companion object {
    @JvmStatic
    fun fixed(parameter: String) = JavaTargetParameter(TargetValue.fixed(parameter), TargetPaths.unordered())
  }
}
