// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.copyReference

import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.ide.actions.copyReference.CopyReferenceTransferable.Companion.DATA_FLAVOR
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.DataFlavor.stringFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

/**
 * The platform disposes this transferable when its project closes, or a plugin unloads. Once a transferable is disposed of it can no longer
 * be used to retrieve a specific [CopyReferenceTarget] for custom [com.intellij.ide.PasteProvider]s, however, simple paste actions will
 * continue to work correctly via [reference]. N.B., the transferable is not disposed if it is garbage collected (e.g., due to the clipboard
 * manager no longer referencing it).
 *
 * @see CopyReferenceTransferablePluginListener
 * @see CopyReferenceTarget.fromTransferable
 */
internal class CopyReferenceTransferable private constructor(
  @Volatile
  private var pointer: Pointer<out CopyReferenceTarget>?, private val reference: String,
) : Transferable, Disposable {

  /**
   * The target of this transferable, or `null` if the target is no longer valid, or the transferable is disposed.
   */
  @get:RequiresReadLock
  val target: CopyReferenceTarget?
    get(): CopyReferenceTarget? = pointer?.dereference()

  @RequiresReadLock
  @RequiresBackgroundThread
  constructor(target: CopyReferenceTarget) : this(target.createPointer(), target.reference)

  override fun dispose() {
    pointer = null
  }

  override fun getTransferDataFlavors(): Array<out DataFlavor> = arrayOf(DATA_FLAVOR, CopyReferenceAction.ourFlavor, stringFlavor)

  /**
   * For [DATA_FLAVOR], returns the [target] and requires a read lock. Throws [IOException] if the target is no longer valid.
   * For the other flavors, returns the reference string and requires no lock.
   */
  override fun getTransferData(flavor: DataFlavor?): Any {
    if (DATA_FLAVOR == flavor) {
      return target ?: throw IOException("Data is no longer available")
    }

    if (CopyReferenceAction.ourFlavor == flavor || stringFlavor == flavor) {
      return reference
    }

    throw UnsupportedFlavorException(flavor)
  }

  override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor != null && transferDataFlavors.contains(flavor)

  override fun toString(): String = "CopyReferenceTransferable(pointer=$pointer, reference='$reference')"

  companion object {
    private val DATA_FLAVOR: DataFlavor = FileCopyPasteUtil.createJvmDataFlavor(CopyReferenceTarget::class.java)

    @JvmStatic
    @RequiresReadLock
    fun targetOf(transferable: Transferable): CopyReferenceTarget? {
      if (!transferable.isDataFlavorSupported(DATA_FLAVOR)) {
        return null
      }

      return try {
        transferable.getTransferData(DATA_FLAVOR) as? CopyReferenceTarget
      }
      catch (_: IOException) {
        null
      }
      catch (_: UnsupportedFlavorException) {
        null
      }
    }
  }
}
