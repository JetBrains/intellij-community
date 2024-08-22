// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs

import io.kotest.assertions.withClue
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.walk

class MultiRoutingFileSystemProviderTest {
  @Nested
  inner class `everything must return MultiRoutingFsPath` {
    val provider = MultiRoutingFileSystemProvider(defaultSunNioFs.provider())
    val rootDirectory = provider.getFileSystem(URI("file:/")).rootDirectories.first()
    val childOfRootDirectory = rootDirectory.listDirectoryEntries().first()

    @Test
    fun `getPath of URI`() {
      provider.getPath(defaultSunNioFs.rootDirectories.first().toUri()).shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun newDirectoryStream() {
      provider.newDirectoryStream(rootDirectory, { true }).use { pathIter ->
        for (path in pathIter) {
          withClue(path.toString()) {
            path.shouldBeInstanceOf<MultiRoutingFsPath>()
          }
        }
      }
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun readSymbolicLink() {
      val path =
        rootDirectory
          .walk(PathWalkOption.BREADTH_FIRST)
          .take(10_000)  // This number was taken at random.
          .firstNotNullOfOrNull { path ->
            try {
              provider.readSymbolicLink(path)
            }
            catch (_: java.nio.file.FileSystemException) {
              null
            }
            catch (_: UnsupportedOperationException) {
              Assumptions.abort("This OS does not support symbolic links")
            }
          }
        ?: Assumptions.abort("No symlink found on the filesystem")

      path.shouldBeInstanceOf<MultiRoutingFsPath>()
    }
  }
}