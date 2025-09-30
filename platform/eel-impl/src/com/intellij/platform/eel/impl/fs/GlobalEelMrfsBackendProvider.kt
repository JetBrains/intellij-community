// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.impl.fs.telemetry.MeasuringFileSystemListener
import com.intellij.platform.eel.provider.MultiRoutingFileSystemBackend
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path

@VisibleForTesting
@ApiStatus.Internal
object GlobalEelMrfsBackendProvider {
  private val isComputing = ThreadLocal<Unit>()

  init {
    // Trigger class loading of the necessary extension point interface before this BackendProvider is registered.
    MultiRoutingFileSystemBackend.EP_NAME.extensionList
  }

  fun install(provider: MultiRoutingFileSystemProvider) {
    provider.theOnlyFileSystem.setBackendProvider(::compute, ::getCustomRoots, ::getCustomFileStores)
    if (System.getProperty("nio.mrfs.telemetry.enable", "false").toBoolean()) {
      provider.setTraceListener(MeasuringFileSystemListener())
    }
  }

  fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem {
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
    return localFS
  }

  fun getCustomRoots(localFS: FileSystem): Collection<Path> =
    MultiRoutingFileSystemBackend.EP_NAME.extensionList.flatMap { eelProvider ->
      eelProvider.getCustomRoots().map { localFS.getPath(it) }
    }

  fun getCustomFileStores(localFS: FileSystem): Collection<FileStore> =
    MultiRoutingFileSystemBackend.EP_NAME.extensionList.flatMap { eelProvider ->
      eelProvider.getCustomFileStores(localFS)
    }
}