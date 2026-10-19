// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.DefaultDebugEnvironment
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.impl.hotswap.SourceFileChangeCompatibilityChecker
import org.jetbrains.annotations.ApiStatus

/**
 * Allows plugins to customize source change compatibility checks for HotSwap.
 */
@ApiStatus.Internal
interface HotSwapSourceChangeCompatibilityCheckerProvider {

  fun provideCheckersForSession(debuggerSession: DebuggerSession): List<SourceFileChangeCompatibilityChecker>

  /** Returns true to disable compatibility checks from all providers for this session, i.e. always try to do HotSwap. */
  fun shouldSuppressCompatibilityChecks(debuggerSession: DebuggerSession): Boolean = false

  companion object {
    private val EP_NAME: ExtensionPointName<HotSwapSourceChangeCompatibilityCheckerProvider> =
      ExtensionPointName("com.intellij.debugger.hotSwapSourceChangeCompatibilityCheckerProvider")

    fun findCompatibilityCheckersForSession(debuggerSession: DebuggerSession): List<SourceFileChangeCompatibilityChecker> {
      if (!Registry.`is`("debugger.hotswap.source.change.compatibility.checks.enabled")) return emptyList()
      if (DefaultDebugEnvironment.hasEnhancedClassRedefinitionEnabled(debuggerSession.debugEnvironment)) return emptyList()
      val providers = EP_NAME.extensionList
      if (providers.any { it.shouldSuppressCompatibilityChecks(debuggerSession) }) return emptyList()
      return providers.flatMap { it.provideCheckersForSession(debuggerSession) }
    }
  }
}
