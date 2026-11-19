// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.target.value.TargetValue
import org.jetbrains.annotations.ApiStatus

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
   *
   * Resolve an instance once: call this function or [toLocalParameter] only one time.
   * A second call throws [IllegalStateException].
   * Build a new instance for each command line setup.
   */
  fun resolvePaths(
    uploadPathsResolver: (TargetPath) -> TargetValue<String>,
    downloadPathsResolver: (TargetPath) -> TargetValue<String>
  ) {
    targetPaths.resolveAll(uploadPathsResolver, downloadPathsResolver)
  }

  /**
   * Returns the parameter with paths resolved to local versions.
   *
   * This function resolves the paths of the instance, as [resolvePaths] does.
   * A second call of either function throws [IllegalStateException].
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

    private val targetValues: MutableList<TargetValue<String>> = mutableListOf()

    /**
     * Adds given string as-is to the overall parameter.
     */
    fun fixed(value: String): Builder = apply { targetValues += TargetValue.fixed(value) }

    /**
     * Adds the target version of the given local path. The path must be declared in the target paths of this builder.
     * The value completes after [JavaTargetParameter.resolvePaths] or [JavaTargetParameter.toLocalParameter] resolves the path.
     *
     * @throws IllegalArgumentException if the target paths of this builder do not declare [value].
     */
    fun toResolve(value: String): Builder = apply { targetValues += targetPaths.resolveAsync(value) }

    @Deprecated("Use toResolve", ReplaceWith("toResolve(value)"))
    fun resolved(value: String): Builder = toResolve(value)

    fun build(): JavaTargetParameter {
      return JavaTargetParameter(TargetValue.composite(targetValues) { it.joinToString("") }, targetPaths)
    }
  }

  companion object {
    @JvmStatic
    fun fixed(parameter: String): JavaTargetParameter = JavaTargetParameter(TargetValue.fixed(parameter), TargetPaths.unordered())
  }
}
