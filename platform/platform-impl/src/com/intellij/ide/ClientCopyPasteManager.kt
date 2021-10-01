// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/**
 * A per-client service managing clipboard.
 * Take a look at [CopyPasteManagerEx]
 */
@ApiStatus.Internal
interface ClientCopyPasteManager : ClipboardOwner {
  companion object {
    @JvmStatic
    fun getCurrentInstance(): ClientCopyPasteManager = service()
  }

  fun areDataFlavorsAvailable(vararg flavors: DataFlavor): Boolean
  fun setContents(content: Transferable)
  fun getContents(): Transferable?
  fun <T> getContents(flavor: DataFlavor): T?
  fun stopKillRings()
  fun stopKillRings(document: Document)
  fun getAllContents(): Array<Transferable>
  fun removeContent(t: Transferable?)
  fun moveContentToStackTop(t: Transferable?)
}