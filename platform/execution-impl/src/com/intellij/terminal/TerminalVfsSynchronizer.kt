// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.ide.GeneralSettings
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.util.addFocusListener
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

private val LOG = fileLogger()

/**
 * Saves all documents to the VFS when [component] gains focus.
 * Refresh VFS when [component] loses focus.
 * The focus listener is added when the component is added to the UI hierarchy.
 */
@ApiStatus.Internal
fun refreshVfsOnFocusChange(component: Component, coroutineScope: CoroutineScope) {
  val saveAllDocumentsRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  coroutineScope.launch(Dispatchers.IO + CoroutineName("Terminal focus gained: save all documents")) {
    saveAllDocumentsRequests.collect {
      LOG.debug { "Focus gained, save all documents to VFS." }
      FileDocumentManager.getInstance().saveAllDocuments()
      ManagingFS.getInstance().flushPendingUpdatesOrNotify()
    }
  }

  component.addFocusListener(coroutineScope.asDisposable(), object : FocusListener {
    override fun focusGained(e: FocusEvent) {
      if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
        check(saveAllDocumentsRequests.tryEmit(Unit))
      }
    }

    override fun focusLost(e: FocusEvent) {
      if (GeneralSettings.getInstance().isSyncOnFrameActivation) {
        // Like we sync the external changes when switching to the IDE window, let's do the same
        // when the focus is transferred from the built-in terminal to some other IDE place.
        // To get the updates from a long-running command in the built-in terminal.
        LOG.debug { "Focus lost, schedule VFS refresh." }
        SaveAndSyncHandler.getInstance().scheduleRefresh()
      }
    }
  })
}

@ApiStatus.Internal
/** Hack for a Java code with no coroutine scopes */
internal fun refreshVfsOnFocusChange(component: Component, parentDisposable: Disposable) {
  val scope = service<TerminalCoroutineScopeProvider>().coroutineScope.childScope("Terminal VFS refresh on focus change")
  scope.coroutineContext.job.cancelOnDispose(parentDisposable)
  refreshVfsOnFocusChange(component, scope)
}

@Service(Service.Level.APP)
private class TerminalCoroutineScopeProvider(val coroutineScope: CoroutineScope)