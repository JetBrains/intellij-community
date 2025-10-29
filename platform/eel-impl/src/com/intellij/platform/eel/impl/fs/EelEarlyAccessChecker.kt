// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandles

@Service
@ApiStatus.Internal
class EelEarlyAccessChecker {
  private val poorMansBloomFilter = Array(1 shl 16) { true }
  private val varHandle = MethodHandles.arrayElementVarHandle(poorMansBloomFilter::class.java)

  fun check(path: String) {
    if (!EDT.isCurrentThreadEdt()) {
      return
    }

    val hash = path.hashCode().let {
      (it and 0xFFFF) xor (it ushr 16 and 0xFFFF)
    }
    if (!(varHandle.getAndSet(poorMansBloomFilter, hash, false) as Boolean)) {
      return
    }

    val application = ApplicationManagerEx.getApplicationEx()
    if (application?.isUnitTestMode != false) {
      return
    }

    // TODO Remove later.
    if (ApplicationManagerEx.isInIntegrationTest()) {
      return
    }

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
    // A: It does. However, IJent initialization is much heavier than accessing some files over IJent.
    //    This error is shown when the initialization happens in EDT, which can freeze the UI.
    //
    // Q: Isn't accessing the file system in EDT a bad practice in general?
    // A: In general, yes. However, there's too much code that does it anyway.
    //    This error highlights at least the most problematic code.
    LOG.error("Remote file system accessed before Eel initialization. The description is in the source code.")
  }

  companion object {
    private val LOG = logger<EelEarlyAccessChecker>()
  }
}