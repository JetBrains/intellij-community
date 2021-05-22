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
    uploadPathsResolver: (String) -> TargetValue<String>,
    downloadPathsResolver: (String) -> TargetValue<String>
  ) {
    targetPaths.resolveAll(uploadPathsResolver, downloadPathsResolver)
  }

  /**
   * Returns the parameter with paths resolved to local versions.
   */
  fun toLocalParameter(): String {
    targetPaths.resolveAll(
      uploadPathsResolver = { TargetValue.fixed(it) },
      downloadPathsResolver = { TargetValue.fixed(it) }
    )
    return parameter.targetValue.blockingGet(0)!!
  }

  class Builder(
    uploadPaths: Set<String> = setOf(),
    downloadPaths: Set<String> = setOf()
  ) {
    private val targetPaths: TargetPaths = TargetPaths(uploadPaths, downloadPaths)

    private val parameterBuilderParts: MutableList<() -> String> = mutableListOf()
    private val asyncActions: MutableList<() -> Unit> = mutableListOf()

    /**
     * Adds given string as-is to the overall parameter.
     */
    fun fixed(value: String) = apply { parameterBuilderParts += { value } }

    /**
     * Adds string that is a resolved version of given one. If it is a file path, then it will be resolved to
     * satisfy file system on target.
     */
    fun resolved(value: String) = apply { parameterBuilderParts += { getResolved(value) } }

    /**
     * Requests some action to be done asynchronously when given value will be resolved.
     * It may be used, for example, when resolved string is needed somewhere outside the parameter,
     * e.g. in configuration file.
     */
    fun doWithResolved(value: String, block: (String) -> Unit) = doAsync { block(getResolved(value)) }

    private fun doAsync(block: () -> Unit) = apply { asyncActions += block }

    fun build(): JavaTargetParameter {
      val parameter = TargetValue.map(TargetValue.EMPTY_VALUE) {
        asyncActions.forEach { it() }
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

  private class TargetPaths(val uploadPaths: Set<String> = emptySet(),
                            val downloadPaths: Set<String> = emptySet()) {

    private var resolvedPaths: Map<String, TargetValue<String>>? = null

    fun resolveAll(uploadPathsResolver: (String) -> TargetValue<String>,
                   downloadPathsResolver: (String) -> TargetValue<String>) {
      resolvedPaths = uploadPaths.associateWith(uploadPathsResolver) + downloadPaths.associateWith(downloadPathsResolver)
    }

    fun getResolved(localValue: String): TargetValue<String> = resolvedPaths?.get(localValue)
                                                               ?: throw IllegalStateException("Path $localValue is not resolved")
  }

  companion object {
    @JvmStatic
    fun fixed(parameter: String) = JavaTargetParameter(TargetValue.fixed(parameter), TargetPaths())
  }
}
