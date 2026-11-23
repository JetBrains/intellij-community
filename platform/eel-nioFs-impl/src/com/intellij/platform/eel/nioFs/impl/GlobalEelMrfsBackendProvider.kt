// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.nioFs.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.DelegatingFileSystem
import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystem
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.core.nio.fs.MultiRoutingFsPath
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelCopyMoveImpl
import com.intellij.platform.eel.nioFs.impl.telemetry.MeasuringFileSystemListener
import com.intellij.platform.eel.provider.EelDescriptorOwner
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.annotations.VisibleToClasses
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider

@VisibleForTesting
@ApiStatus.Internal
object GlobalEelMrfsBackendProvider {
  private val isComputing = ThreadLocal<Unit>()

  @Volatile
  private lateinit var localBackend: FileSystem

  init {
    // Trigger class loading of the necessary extension point interface and local backend classes before this BackendProvider is registered.
    MultiRoutingFileSystemBackend.EP_NAME.extensionList
    EelCopyMoveImpl.hasEelCopyOption(emptyArray())
  }

  @VisibleToClasses(
    "com.intellij.platform.ijent.benchmarks.MultiRoutingFileSystemBackendBenchmark",
  )
  fun install(provider: MultiRoutingFileSystemProvider) {
    val localFS = provider.theOnlyFileSystem.delegate
    localBackend = LocalEelMrfsFileSystemProvider(localFS.provider()).wrapDelegateFileSystem(localFS)
    provider.theOnlyFileSystem.setBackendProvider(::compute, ::getCustomRoots, ::getCustomFileStores)
    if (System.getProperty("nio.mrfs.telemetry.enable", "false").toBoolean()) {
      provider.setTraceListener(MeasuringFileSystemListener())
    }
  }

  internal fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem {
    if (isComputing.get() == null) {
      try {
        isComputing.set(Unit)

        // TODO These errors happen often in tests if MultiRoutingFileSystemBackend.EP_NAME.extensionList is called.
        //  I haven't figured out why.
        //  Therefore, a strange logic with getExtensionPointIfRegistered is used here.

        val nonDefaultCandidate =
          ApplicationManager.getApplication()
            ?.extensionArea
            ?.getExtensionPointIfRegistered<MultiRoutingFileSystemBackend>(MultiRoutingFileSystemBackend.EP_NAME.name)
            ?.extensionList
            ?.firstNotNullOfOrNull { backend ->
              try {
                backend.compute(localFS, sanitizedPath)
              }
              catch (err: Exception) {
                logger<GlobalEelMrfsBackendProvider>().error("$backend threw an error trying to handle $sanitizedPath", err)
                null
              }
            }
        if (nonDefaultCandidate != null) {
          return nonDefaultCandidate
        }
      }
      finally {
        isComputing.set(null)
      }
    }
    return localBackend
  }

  internal fun getCustomRoots(localFS: FileSystem): Collection<Path> =
    MultiRoutingFileSystemBackend.EP_NAME.extensionList.flatMap { eelProvider ->
      eelProvider.getCustomRoots().map { localFS.getPath(it) }
    }

  internal fun getCustomFileStores(localFS: FileSystem): Collection<FileStore> =
    MultiRoutingFileSystemBackend.EP_NAME.extensionList.flatMap { eelProvider ->
      eelProvider.getCustomFileStores(localFS)
    }
}

internal class LocalEelMrfsFileSystem(
  private val provider: LocalEelMrfsFileSystemProvider,
  private val delegate: FileSystem,
) : DelegatingFileSystem<LocalEelMrfsFileSystemProvider>(), EelDescriptorOwner {
  override val eelDescriptor: EelDescriptor
    get() = LocalEelDescriptor

  override fun getDelegate(): FileSystem = delegate

  override fun provider(): LocalEelMrfsFileSystemProvider = provider
}

internal class LocalEelMrfsFileSystemProvider(
  private val delegate: FileSystemProvider,
) : DelegatingFileSystemProvider<LocalEelMrfsFileSystemProvider, LocalEelMrfsFileSystem>(), EelDescriptorOwner {
  override val eelDescriptor: EelDescriptor
    get() = LocalEelDescriptor

  override fun wrapDelegateFileSystem(delegateFs: FileSystem): LocalEelMrfsFileSystem =
    LocalEelMrfsFileSystem(this, delegateFs)

  override fun getDelegate(path1: Path?, path2: Path?): FileSystemProvider = delegate

  override fun wrapDelegatePath(delegatePath: Path?): Path? = delegatePath

  override fun toDelegatePath(path: Path?): Path? = path

  override fun canHandleRouting(path: Path): Boolean = false

  override fun newDirectoryStream(dir: Path?, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path>? =
    delegate.newDirectoryStream(dir, filter)

  override fun copy(source: Path, target: Path, vararg options: CopyOption) {
    if (EelCopyMoveImpl.hasEelCopyOption(options)) {
      EelCopyMoveImpl.copy(wrapInMrfsIfNeeded(source), wrapInMrfsIfNeeded(target), *options)
      return
    }
    super.copy(source, target, *options)
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption) {
    if (EelCopyMoveImpl.hasEelCopyOption(options)) {
      EelCopyMoveImpl.move(wrapInMrfsIfNeeded(source), wrapInMrfsIfNeeded(target), *options)
      return
    }
    super.move(source, target, *options)
  }

  private fun wrapInMrfsIfNeeded(path: Path): Path {
    if (path is MultiRoutingFsPath) return path
    val defaultFs = FileSystems.getDefault()
    return if (defaultFs is MultiRoutingFileSystem) {
      defaultFs.provider().wrapDelegatePath(path) ?: path
    }
    else {
      path
    }
  }
}