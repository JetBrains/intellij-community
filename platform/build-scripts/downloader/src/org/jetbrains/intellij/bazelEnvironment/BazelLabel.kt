// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.bazelEnvironment

import org.jetbrains.annotations.ApiStatus

// https://bazel.build/rules/lib/builtins/Label.html
@ApiStatus.Experimental
data class BazelLabel(
  val repo: String,
  val packageName: String,
  val target: String,
) {
  companion object {
    private val regex = Regex("(@[a-zA-Z0-9_-]+)?//([a-zA-Z0-9_./-]+)?:([/a-zA-Z0-9._-]+)")
    fun fromString(label: String): BazelLabel {
      val match = regex.matchEntire(label) ?: error("Bazel label must match '${regex.pattern}': $label")
      val bazelLabel = BazelLabel(
        repo = match.groupValues[1].trimStart('@'),
        packageName = match.groupValues[2],
        target = match.groupValues[3],
      )
      check(bazelLabel.asLabel == label) { "Label from BazelLabel.asLabel must be equal to parsed label (roundtrip) '$label': ${bazelLabel.asLabel}" }
      return bazelLabel
    }
  }

  val asLabel: String
    get() = "${if (repo.isEmpty()) "" else "@"}$repo//$packageName:$target"
}