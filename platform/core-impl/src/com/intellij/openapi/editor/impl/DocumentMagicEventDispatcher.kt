// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEventDispatcher
import com.intellij.openapi.editor.ex.ElfCandidate
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.EDT
import kotlin.concurrent.Volatile

internal abstract class DocumentMagicEventDispatcher(
  settingsElf: DocumentSettings,
  settingsReal: DocumentSettings,
) : DocumentEventDispatcherImpl(settingsReal) {
  private val textElf: DocumentTextUpdate = DocumentTextUpdate.Elf(settingsElf, listeners)
  private val textBoth: DocumentTextUpdate = DocumentTextUpdate.Both(settingsReal, listeners)
  private val bulkElf: DocumentBulkUpdate = DocumentBulkUpdate.Elf(settingsElf, listeners)
  private val bulkBoth: DocumentBulkUpdate = DocumentBulkUpdate.Both(settingsReal, listeners)
  private val dispatcherElf: DocumentEventDispatcher = DocumentElfEventDispatcherImpl()
  private val dispatcherReal: DocumentEventDispatcher = DocumentRealEventDispatcherImpl()

  @Volatile private var firingElfTextChangeOutsideElfScope = false

  protected abstract fun getSnapshotSnapshot(): SnapshotSnapshot

  fun elf(): DocumentEventDispatcher {
    return dispatcherElf
  }

  fun real(): DocumentEventDispatcher {
    return dispatcherReal
  }

  fun <T> withFiringElfTextUpdate(
    revertedEvent: DocumentEvent?,
    changeEvent: DocumentEvent,
    action: () -> T,
  ): T {
    firingElfTextChangeOutsideElfScope = true
    try {
      return textElf.withFiringTextUpdate(changeEvent, revertedEvent, action)
    } finally {
      firingElfTextChangeOutsideElfScope = false
    }
  }

  fun <T> withFiringBothTextUpdate(changeEvent: DocumentEvent, action: () -> T): T {
    return textBoth.withFiringTextUpdate(changeEvent, null, action)
  }

  /**
   * Some ELF changes are delivered after leaving ELF scope, but @ElfCandidate listeners receive them as ordinary document events.
   * Keep the host document on the ELF view while those listeners are notified so event.document reads match the ELF snapshot.
   */
  fun isFiringElfTextChangeOutsideElfScope(): Boolean {
    return firingElfTextChangeOutsideElfScope
  }

  fun setBulkElfUpdateStatus(hostDocument: Document, status: Boolean) {
    bulkElf.setBulkUpdateStatus(hostDocument, status)
  }

  fun setBulkBothUpdateStatus(hostDocument: Document, status: Boolean) {
    check(bulkElf.isInBulkUpdate() == bulkUpdate.isInBulkUpdate()) {
      "inconsistent bulk update status detected"
    }
    bulkBoth.setBulkUpdateStatus(hostDocument, status)
  }

  final override fun firingTextChanged(): Boolean {
    throw UnsupportedOperationException("elf or real context dependant, use corresponding view")
  }

  final override fun isInBulkUpdate(): Boolean {
    throw UnsupportedOperationException("elf or real context dependant, use corresponding view")
  }

  final override fun assertNotInBulkUpdate() {
    throw UnsupportedOperationException("elf or real context dependant, use corresponding view")
  }

  final override fun addDocumentListener(listener: DocumentListener, parentDisposable: Disposable) {
    val listenerOrRouter = routeIfElfCandidate(listener)
    super.addDocumentListener(listenerOrRouter, parentDisposable)
  }

  final override fun addDocumentListener(listener: DocumentListener) {
    val listenerOrRouter = routeIfElfCandidate(listener)
    super.addDocumentListener(listenerOrRouter)
  }

  final override fun removeDocumentListener(listener: DocumentListener) {
    val listenerOrRouter = if (isElfCandidate(listener)) {
      getListeners().find {
        it is ElfRouter && it.origin === listener
      } ?: listener
    } else {
      listener
    }
    val success = listeners.remove(listenerOrRouter)
    if (!success) {
      LOG.error(
        "Can't remove document listener ($listenerOrRouter). " +
        "Registered listeners: ${getListeners().contentToString()}"
      )
    }
  }

  final override fun setBulkModeStatus(hostDocument: Document, status: Boolean) {
    // real document only requires WIL; the ELF barrier is EDT-only, so off-EDT bulk updates must stay real-only
    val isEdt = EDT.isCurrentThreadEdt()
    if (!isEdt || elfBarrier()) {
      super.setBulkModeStatus(hostDocument, status)
    } else {
      setBulkBothUpdateStatus(hostDocument, status)
    }
  }

  private fun assertIsInElfScope() {
    if (!Elf.getElf().isInElfScope()) {
      throw IllegalStateException("ElfDocument is mutable only within elf scope")
    }
  }

  private fun elfBarrier(): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    // reading snapshot is safe because elfScope or isDirty can be changed only on EDT
    val snapshot = getSnapshotSnapshot()
    return elfBarrier(snapshot)
  }

  /**
   * Contract: elf should not observe real changes until elf changes are applied to real document
   */
  private fun elfBarrier(snapshot: SnapshotSnapshot): Boolean {
    return snapshot.isDirty || Elf.getElf().isInElfScope()
  }

  private fun routeIfElfCandidate(listener: DocumentListener): DocumentListener {
    return if (isElfCandidate(listener)) {
      if (listener is PrioritizedDocumentListener) {
        PrioritizedElfRouter(listener)
      } else {
        ElfRouter(listener)
      }
    } else {
      listener
    }
  }

  private fun isElfCandidate(listener: DocumentListener): Boolean {
    return listener.javaClass.isAnnotationPresent(ElfCandidate::class.java)
  }

  private inner class DocumentElfEventDispatcherImpl : DocumentEventDispatcher by this {
    override fun setBulkModeStatus(hostDocument: Document, status: Boolean) {
      assertIsInElfScope()
      setBulkElfUpdateStatus(hostDocument, status)
    }

    override fun firingTextChanged(): Boolean {
      return textElf.isInTextUpdate() ||
             textBoth.isInTextUpdate()
    }

    override fun isInBulkUpdate(): Boolean {
      return bulkElf.isInBulkUpdate() ||
             bulkBoth.isInBulkUpdate()
    }

    override fun assertNotInBulkUpdate() {
      bulkElf.assertNotInBulkUpdate()
      bulkBoth.assertNotInBulkUpdate()
    }
  }

  private inner class DocumentRealEventDispatcherImpl : DocumentEventDispatcher by this {
    override fun firingTextChanged(): Boolean {
      return textUpdate.isInTextUpdate() ||
             textBoth.isInTextUpdate()
    }

    override fun isInBulkUpdate(): Boolean {
      return bulkUpdate.isInBulkUpdate() ||
             bulkBoth.isInBulkUpdate()
    }

    override fun assertNotInBulkUpdate() {
      bulkUpdate.assertNotInBulkUpdate()
      bulkBoth.assertNotInBulkUpdate()
    }
  }

  private open class ElfRouter(val origin: DocumentListener) : DocumentListener {
    override fun beforeElfDocumentChange(event: DocumentEvent, revertingEvent: DocumentEvent?) {
      origin.beforeDocumentChange(event)
    }

    override fun elfDocumentChanged(event: DocumentEvent, revertedEvent: DocumentEvent?) {
      origin.documentChanged(event)
    }

    override fun bulkElfUpdateStarting(document: Document) {
      origin.bulkUpdateStarting(document)
    }

    override fun bulkElfUpdateFinished(document: Document) {
      origin.bulkUpdateFinished(document)
    }

    override fun toString(): String {
      return "ElfRouter(origin=$origin)"
    }
  }

  private class PrioritizedElfRouter(
    origin: PrioritizedDocumentListener,
  ) : ElfRouter(origin), PrioritizedDocumentListener {
    private val priority: Int = origin.priority

    override fun getPriority(): Int {
      return priority
    }
  }

  companion object {
    private val LOG: Logger = logger<DocumentMagicEventDispatcher>()
  }
}
