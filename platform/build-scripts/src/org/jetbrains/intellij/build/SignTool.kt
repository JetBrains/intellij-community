// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentMap
import java.nio.file.Path

/**
 * Implement this interface and pass the implementation to [ProprietaryBuildTools] constructor to sign the product's files.
 */
interface SignTool {
  companion object {
    const val LIB_VERSION_OPTION_NAME: String = "libVersion"
  }

  val signNativeFileMode: SignNativeFileMode

  suspend fun signFiles(files: List<Path>, context: BuildContext?, options: PersistentMap<String, String>)

  suspend fun signFilesWithGpg(files: List<Path>, context: BuildContext)

  /**
   * Returns `null` if failed to download and error is not fatal.
   */
  suspend fun getPresignedLibraryFile(path: String, libName: String, libVersion: String, context: BuildContext): Path?

  suspend fun commandLineClient(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Path?
}

enum class SignNativeFileMode {
  PREPARE, ENABLED, DISABLED
}
