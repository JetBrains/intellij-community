// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.indexing.FileBasedIndexTumbler
import com.intellij.util.indexing.ID
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object CacheSwitcher {
  private val log = thisLogger()

  @ApiStatus.Internal
  fun switchIndexAndVfs(cachesDir: Path? = null,
                        indexDir: Path? = null,
                        reason: String,
                        unsafeDataManipulation: () -> Unit = {}) {
    log.info("Switching caches, reason: $reason")
    ApplicationManager.getApplication().invokeAndWait {
      val fileBasedIndexTumbler = FileBasedIndexTumbler(reason)
      fileBasedIndexTumbler.switch {
        val vfs = PersistentFS.getInstance() as PersistentFSImpl
        vfs.switch {
          assertUrlBasedVirtualFilePointers(reason)
          unsafeDataManipulation()

          setupProp("caches_dir", cachesDir)
          setupProp("index_root_path", indexDir)

        }

        ID.reloadEnumFile()
      }
    }
  }

  private fun assertUrlBasedVirtualFilePointers(reason: String) {
    val assertSoftly = ApplicationManager.getApplication().isUnitTestMode
    val virtualFilePointerManagerImpl = VirtualFilePointerManager.getInstance() as VirtualFilePointerManagerImpl

    try {
      virtualFilePointerManagerImpl.assertUrlBasedPointers()
    }
    catch (e: Exception) {
      val message = "Not disposed pointers during caches switching $reason"
      if (assertSoftly) {
        log.info(message, e)
      }
      else {
        log.error(message, e)
      }
    }
  }

  private fun setupProp(name: String, value: Path?) {
    if (value == null) {
      System.clearProperty(name)
    }
    else {
      System.setProperty(name, value.absolutePathString())
    }
  }

  private fun PersistentFSImpl.switch(action: () -> Unit) {
    disconnect()
    try {
      action()
    }
    finally {
      connect()
    }
  }

  private fun FileBasedIndexTumbler.switch(action: () -> Unit) {
    turnOff()
    try {
      action()
    }
    finally {
      turnOn()
    }
  }
}
