// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent.nio

import com.intellij.openapi.util.Disposer
import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.provider.MultiRoutingFileSystemBackend
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.net.URI
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path

/**
 * Allows registering custom file systems
 */
@OptIn(AwaitCancellationAndInvoke::class)
@ApiStatus.Internal
@TestOnly
fun CoroutineScope.registerIjentNioFs(
  ijent: IjentApi,
  root: String,
  authority: String,
  wrapFileSystemProvider: ((FileSystemProvider) -> DelegatingFileSystemProvider<*, *>)? = null,
): Path {
  val root = root.replace('\\', '/')
  val nioRoot = Path(root)

  val uri = URI("ijent", authority, root, null, null)

  val ijentFs = try {
    IjentNioFileSystemProvider.getInstance().newFileSystem(uri, IjentNioFileSystemProvider.newFileSystemMap(ijent.fs))
  }
  catch (_: FileSystemAlreadyExistsException) {
    IjentNioFileSystemProvider.getInstance().getFileSystem(uri)
  }

  val disposable = Disposer.newDisposable()

  val fs = AtomicReference(null as FileSystem?)

  MultiRoutingFileSystemBackend.EP_NAME.point.registerExtension(
    object : MultiRoutingFileSystemBackend {
      override fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem? =
        if (sanitizedPath.startsWith(root) && sanitizedPath.getOrNull(root.length).let { it == null || it == '/' })
          fs.updateAndGet { oldFs ->
            oldFs
            ?: IjentEphemeralRootAwareFileSystemProvider(
              root = nioRoot,
              ijentFsProvider = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance()),
              originalFsProvider = TracingFileSystemProvider(localFS.provider()),
              useRootDirectoriesFromOriginalFs = false
            ).let { wrapFileSystemProvider?.invoke(it) ?: it }.getFileSystem(uri)

          }
        else
          null

      override fun getCustomRoots(): Collection<@MultiRoutingFileSystemPath String> =
        listOf(root)

      override fun getCustomFileStores(localFS: FileSystem): Collection<FileStore> =
        ijentFs.fileStores.toList()
    },
    disposable,
  )

  EelProvider.EP_NAME.point.registerExtension(
    object : EelProvider {
      override suspend fun tryInitialize(@MultiRoutingFileSystemPath path: String) {
        // Nothing.
      }

      override fun getEelDescriptor(@MultiRoutingFileSystemPath path: Path): EelDescriptor? =
        if (path.startsWith(nioRoot)) ijent.descriptor
        else null

      override fun getCustomRoots(eelDescriptor: EelDescriptor): Collection<String>? =
        if (eelDescriptor == ijent.descriptor) listOf(root)
        else null

      override fun getInternalName(eelMachine: EelMachine): String? =
        if (eelMachine == ijent.descriptor.machine) ijent.descriptor.machine.name
        else null

      override fun getEelMachineByInternalName(internalName: String): EelMachine? =
        if (internalName == ijent.descriptor.machine.name) ijent.descriptor.machine
        else null
    },
    disposable,
  )

  awaitCancellationAndInvoke {
    Disposer.dispose(disposable)
  }

  // Compute a path after registration
  return Path(root)
}