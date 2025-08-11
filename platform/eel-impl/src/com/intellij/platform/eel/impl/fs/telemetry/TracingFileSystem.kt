// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import com.intellij.platform.core.nio.fs.DelegatingFileSystem
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
    Measurer.measure(Measurer.Operation.fileSystemClose) {
      delegate.close()
    }
  }

  override fun provider(): TracingFileSystemProvider = provider

  override fun supportedFileAttributeViews(): MutableSet<String> =
    Measurer.measure(Measurer.Operation.supportedFileAttributeViews) {
      super.supportedFileAttributeViews()
    }

  override fun newWatchService(): WatchService =
    Measurer.measure(Measurer.Operation.fileSystemNewWatchService) {
      super.newWatchService()
    }
}
