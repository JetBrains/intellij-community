// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.copyReference

import com.intellij.ide.ClientCopyPasteManager
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.forEachSession
import com.intellij.openapi.util.Disposer
import com.intellij.util.application

internal class CopyReferenceTransferablePluginListener : DynamicPluginListener {
  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    application.forEachSession(ClientKind.ALL) { session ->
      val manager = ClientCopyPasteManager.getInstance(session) ?: return@forEachSession
      for (transferable in manager.allContents.filterIsInstance<CopyReferenceTransferable>()) {
        Disposer.dispose(transferable)
      }
    }
  }
}
