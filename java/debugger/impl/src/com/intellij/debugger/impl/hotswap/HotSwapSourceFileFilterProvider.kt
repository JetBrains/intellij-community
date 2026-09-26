// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.hotswap.HotSwapProvider
import com.intellij.xdebugger.hotswap.SourceFileChangesCollector
import com.intellij.xdebugger.impl.hotswap.SourceFileChangeFilter
import org.jetbrains.annotations.ApiStatus

/**
 * The aim of this interface is to allow different plugins to provide custom filtering of changes staged for HotSwap.
 *
 * Implementations of [HotSwapProvider] should call EPs of this interface upon
 * creation of [SourceFileChangesCollector] to propagate filters.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface HotSwapSourceFileFilterProvider {
  fun provideFiltersForSession(debuggerSession: DebuggerSession): List<SourceFileChangeFilter<VirtualFile>>

  companion object {
    private val EP_NAME: ExtensionPointName<HotSwapSourceFileFilterProvider> = ExtensionPointName("com.intellij.debugger.hotSwapSourceFileFilterProvider")

    fun findSourceFiltersForSession(debuggerSession: DebuggerSession): List<SourceFileChangeFilter<VirtualFile>> {
      return EP_NAME.extensionsIfPointIsRegistered.flatMap { it.provideFiltersForSession(debuggerSession) }
    }
  }
}
