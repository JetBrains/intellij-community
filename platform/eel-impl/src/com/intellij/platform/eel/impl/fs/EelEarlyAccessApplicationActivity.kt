// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.ide.ApplicationActivity
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystem
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import org.jetbrains.annotations.VisibleForTesting
import java.lang.invoke.MethodHandles
import java.nio.file.FileSystems
import java.util.function.BiConsumer

internal class EelEarlyAccessApplicationActivity : ApplicationActivity {
  override suspend fun execute() {
    val fs = FileSystems.getDefault()
    if (fs.javaClass.name != MultiRoutingFileSystem::class.java.name) return

    val logger = logger<EelEarlyAccessApplicationActivity>()
    val filter = EelEarlyAccessFilter()

    MultiRoutingFileSystemProvider.setPathSpy(fs.provider(), BiConsumer<String, Boolean> { strPath, isDefaultProvider ->
      if (isDefaultProvider && strPath.startsWith("\\\\wsl")) {
        if (filter.check(strPath)) {
          // Q: What happened?
          // A: Some code tried to access \\wsl.localhost before the initialization of the IJent file system.
          //    Therefore, the code will access the original filesystem of WSL brought by Microsoft,
          //    and the code won't access our polished file system with various workarounds, optimizations, etc.
          //    Also, the code that triggered this error is an obstacle for implementing DevContainers over Eel.
          //
          // Q: How to fix it?
          // A: Call `com.intellij.platform.eel.provider.EelInitialization.runEelInitialization` in advance.
          //    Sometimes it's easy, sometimes you have to rework the UI/UX, sorry for that.
          //
          // Q: Why not initialize the IJent filesystem lazily, right here, at this moment?
          // A: Although this initialization usually takes a second, sometimes it can be significantly slower.
          //    It depends not only on the I/O speed of the machine, but also on personal configurations of the user.
          //    Being called from EDT, this lazy initialization would lead to yet another freeze.

          // TODO IJPL-190497
          //logger.error(
          //  "Remote file system accessed before Eel initialization. The description is in the source code.",
          //  Attachment("path", strPath.toString()),
          //)
        }
      }
    })
  }
}

@VisibleForTesting
class EelEarlyAccessFilter {
  private val poorMansBloomFilter = Array(1 shl 16) { true }
  private val varHandle = MethodHandles.arrayElementVarHandle(poorMansBloomFilter::class.java)

  fun check(strPath: String): Boolean {
    val hash = strPath.hashCode().let {
      (it and 0xFFFF) xor (it ushr 16 and 0xFFFF)
    }
    return varHandle.getAndSet(poorMansBloomFilter, hash, false) as Boolean
  }
}