// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

/**
 * A file that a layout ships with the executable bit in [distributions].
 *
 * [path] is relative to the directory of the layout, and it can be a glob.
 */
data class ExecutableFileDeclaration(
  @JvmField val path: String,
  @JvmField val distributions: Set<SupportedDistribution>,
)
