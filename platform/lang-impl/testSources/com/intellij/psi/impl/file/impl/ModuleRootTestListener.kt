// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener

class ModuleRootTestListener: ModuleRootListener {
  private val myBuffer: StringBuffer = StringBuffer()
  private val logger = thisLogger()

  private fun logEvent(message: String){
    myBuffer.append(message)
    logger.debug(Throwable(message))
  }

  fun reset() {
    logger.debug("reset")
    myBuffer.setLength(0)
  }

  val eventsString: String
    get() = myBuffer.toString()

  override fun beforeRootsChange(event: ModuleRootEvent) {
    logEvent("beforeRootsChange\n")
  }

  override fun rootsChanged(event: ModuleRootEvent) {
    logEvent("rootsChanged\n")
  }
}