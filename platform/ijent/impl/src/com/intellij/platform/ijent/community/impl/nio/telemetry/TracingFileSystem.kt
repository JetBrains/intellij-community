// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio.telemetry

import com.intellij.platform.core.nio.fs.DelegatingFileSystem
import com.intellij.platform.ijent.community.impl.nio.telemetry.Measurer.Operation.*
import java.nio.file.FileSystem
import java.nio.file.WatchService

class TracingFileSystem(
  private val provider: TracingFileSystemProvider,
  private val delegate: FileSystem,
) : DelegatingFileSystem<TracingFileSystemProvider>() {
  public override fun getDelegate(): FileSystem = delegate

  override fun getDelegate(root: String): FileSystem = delegate

  override fun toString(): String = """${javaClass.simpleName}($delegate)"""

  override fun close() {
    Measurer.measure(fileSystemClose) {
      delegate.close()
    }
  }

  override fun provider(): TracingFileSystemProvider = provider

  override fun supportedFileAttributeViews(): MutableSet<String> =
    Measurer.measure(supportedFileAttributeViews) {
      super.supportedFileAttributeViews()
    }

  override fun newWatchService(): WatchService =
    Measurer.measure(fileSystemNewWatchService) {
      super.newWatchService()
    }
}
