// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelMachineNioFileSystemProvider.Bridge
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.util.ServiceLoader
import java.util.WeakHashMap


/**
 * Returns the machine-bound [FileSystem] for this [EelApi] environment.
 *
 * For [LocalEelApi], this property returns [FileSystems.getDefault], which is `MultiRoutingFileSystem`, not the original default file system.
 * Therefore, the returned file system can return files that do not exist locally.
 */
@get:ApiStatus.Experimental
val EelApi.fileSystem: FileSystem
  get() = EelMachineNioFileSystemProvider.registry.getFileSystem(this)

@ApiStatus.Internal
interface EelMachineNioFileSystemProvider {
  fun createFileSystemForEelApi(eelApi: EelApi): FileSystem? = null

  /**
   * SPI for providing [EelMachineNioFileSystemProvider] instances. Loaded via [ServiceLoader] from `eel-nioFs-impl`.
   */
  @ApiStatus.Internal
  fun interface Bridge {
    fun getAll(): List<EelMachineNioFileSystemProvider>
  }

  companion object {
    val registry: EelMachineNioFileSystemRegistry by lazy { EelMachineNioFileSystemRegistry() }
  }
}

@ApiStatus.Internal
class EelMachineNioFileSystemRegistry {
  private val apiFileSystems = WeakHashMap<EelApi, FileSystem>()

  private val providerBridge: Bridge? = ServiceLoader.load(Bridge::class.java, Bridge::class.java.classLoader).firstOrNull()

  init {
    EelMachineConnectionProvider.registry.addListener(object : EelMachineConnectionRegistry.Listener {
      override fun onConnectionOpened(eelApi: EelApi) {
        findOrCreateFileSystem(eelApi)
      }

      override fun isConnectionStillAlive(eelApi: EelApi): Boolean {
        val fileSystem = synchronized(apiFileSystems) { apiFileSystems[eelApi] } ?: return true
        return fileSystem.isOpen
      }

      override fun onConnectionClosed(eelApi: EelApi) {
        val fileSystemToClose = synchronized(apiFileSystems) {
          apiFileSystems.remove(eelApi)
        }
        if (fileSystemToClose != null) {
          runCatching {
            fileSystemToClose.close()
          }
        }
      }
    })
  }

  private fun findOrCreateFileSystem(eelApi: EelApi): FileSystem? {
    if (eelApi is LocalEelApi) {
      return FileSystems.getDefault()
    }
    synchronized(apiFileSystems) {
      val existing = apiFileSystems[eelApi]
      if (existing != null && existing.isOpen) {
        return existing
      }
      val created = providerBridge?.getAll()?.firstNotNullOfOrNull { it.createFileSystemForEelApi(eelApi) } ?: return null
      apiFileSystems[eelApi] = created
      return created
    }
  }

  @ApiStatus.Internal
  fun getFileSystem(eelApi: EelApi): FileSystem {
    return findOrCreateFileSystem(eelApi)
           ?: error("No FileSystem available for $eelApi (machine=${eelApi.machine})")
  }
}
