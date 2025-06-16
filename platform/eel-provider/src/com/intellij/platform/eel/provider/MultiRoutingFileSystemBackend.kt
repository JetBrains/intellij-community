// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileStore
import java.nio.file.FileSystem

/**
 * An extension point for [com.intellij.platform.core.nio.fs.MultiRoutingFileSystem.BackendProvider].
 */
@ApiStatus.Internal
interface MultiRoutingFileSystemBackend {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MultiRoutingFileSystemBackend> = ExtensionPointName("com.intellij.multiRoutingFileSystemBackend")
  }

  /**
   * Initialization of this interface assigns [MultiRoutingFileSystemBackend] as the backend provider.
   */
  @ApiStatus.Internal
  interface InitializationService

  /**
   * In contrast with [com.intellij.platform.core.nio.fs.MultiRoutingFileSystem.BackendProvider], it must never return `localFS`.
   */
  fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem?

  fun getCustomRoots(): Collection<String>

  fun getCustomFileStores(localFS: FileSystem): Collection<FileStore>
}