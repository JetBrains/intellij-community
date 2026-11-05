// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import java.nio.file.Path

/**
 * One file that [ProductProperties.additionalOsSpecificFiles] declares for the distribution of one OS and architecture.
 *
 * [label] is the Bazel label of the file, which a split dev distribution places. [resolve] finds the file for a build
 * that copies it. [relativePath] is the destination under the distribution root.
 *
 * [executable] is the mode a split dev distribution gives the file. A production build takes the mode of the source
 * file and then applies the executable patterns of the OS. A file that a program runs states `true`.
 */
class OsSpecificDistFile(
  @JvmField val label: String,
  @JvmField val relativePath: String,
  @JvmField val executable: Boolean = false,
  private val resolver: () -> Path,
) {
  fun resolve(): Path = resolver()

  override fun toString(): String = "$label -> $relativePath"
}
