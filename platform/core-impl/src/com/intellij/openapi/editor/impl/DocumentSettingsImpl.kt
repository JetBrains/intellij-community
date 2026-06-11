// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.core.CoreBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.ThreadingAssertions
import java.util.concurrent.atomic.AtomicReference

internal class DocumentSettingsImpl private constructor(
  private val isWriteAccessCheckEnabled : Boolean,
  private val isCommandCheckEnabled : Boolean,
  private val isPCEWarningEnabled : Boolean,
  private val mutableSettings: AtomicReference<Settings>,
) : DocumentSettings {
  constructor(
    assertThreading: Boolean,
    acceptSlashR: Boolean,
    charSequence: CharSequence,
  ) : this(
    isWriteAccessCheckEnabled=assertThreading,
    isCommandCheckEnabled=assertThreading,
    isPCEWarningEnabled=assertThreading,
    mutableSettings=AtomicReference(Settings(acceptSlashR)),
  ) {
    assertValidSeparators(charSequence)
  }

  // @formatter:off
  private data class Settings(
    val isSlashRAllowed                     : Boolean,
    val isStripTrailingSpacesEnabled        : Boolean = true,
    val cycleBufferSize                     : Int = 0,
    val isGuardCheckEnabled                 : Int = 0,
    val isGuardCheckSuppressed              : Boolean = false,
    val isGuardCheckSuppressedWholeTextOnly : Boolean = false,
    val isReadOnly                          : Boolean = false,
    val readonlyHandler                     : ReadonlyFragmentModificationHandler? = null,
  )
  // @formatter:on

  override fun isWriteAccessCheckEnabled(): Boolean {
    return isWriteAccessCheckEnabled
  }

  override fun isCommandCheckEnabled(): Boolean {
    return isCommandCheckEnabled
  }

  override fun isPCEWarningEnabled(): Boolean {
    return isPCEWarningEnabled
  }

  override fun isSlashRAllowed(): Boolean {
    return mutableSettings.get().isSlashRAllowed
  }

  override fun setSlashRAllowed(accept: Boolean): Boolean {
    val old = mutableSettings.getAndUpdate { it.copy(isSlashRAllowed=accept) }
    return old.isSlashRAllowed
  }

  override fun assertValidSeparators(charsToValidate: CharSequence) {
    if (!isSlashRAllowed) {
      StringUtil.assertValidSeparators(charsToValidate)
    }
  }

  override fun cycleBufferSize(): Int {
    return mutableSettings.get().cycleBufferSize
  }

  override fun setCycleBufferSize(buffer: Int) {
    assert(buffer >= 0) {
      "Buffer size must be positive, got: $buffer"
    }
    mutableSettings.getAndUpdate { it.copy(cycleBufferSize=buffer) }
  }

  override fun isStripTrailingSpacesEnabled(): Boolean {
    return mutableSettings.get().isStripTrailingSpacesEnabled
  }

  override fun setStripTrailingSpaces(strip: Boolean) {
    mutableSettings.getAndUpdate { it.copy(isStripTrailingSpacesEnabled=strip) }
  }

  override fun isGuardCheckEnabled(wholeTextReplaced: Boolean): Boolean {
    val settings = mutableSettings.get()
    val suppressed = (settings.isGuardCheckSuppressed) ||
                     (settings.isGuardCheckSuppressedWholeTextOnly && wholeTextReplaced)
    return settings.isGuardCheckEnabled > 0 && !suppressed
  }

  override fun startGuardCheck() {
    mutableSettings.getAndUpdate { it.copy(isGuardCheckEnabled=(it.isGuardCheckEnabled + 1)) }
  }

  override fun stopGuardCheck() {
    val old = mutableSettings.getAndUpdate { it.copy(isGuardCheckEnabled=(it.isGuardCheckEnabled - 1)) }
    LOG.assertTrue(old.isGuardCheckEnabled > 0) {
      "Unpaired start/stopGuardedBlockChecking"
    }
  }

  override fun suppressGuardCheck(onlyWholeText: Boolean) {
    suppressGuardCheck(true, onlyWholeText)
  }

  override fun unsuppressGuardCheck(onlyWholeText: Boolean) {
    suppressGuardCheck(false, onlyWholeText)
  }

  override fun setReadOnly(readOnly: Boolean): Boolean {
    val old = mutableSettings.getAndUpdate { it.copy(isReadOnly=readOnly) }
    val oldValue = old.isReadOnly
    if (oldValue != readOnly) {
      if (LOG.isTraceEnabled) {
        LOG.trace(Throwable("Setting readonly flag $oldValue -> $readOnly"))
      }
    }
    return oldValue
  }

  override fun readOnlyHandler(): ReadonlyFragmentModificationHandler? {
    return mutableSettings.get().readonlyHandler
  }

  override fun setReadOnlyHandler(readonlyHandler: ReadonlyFragmentModificationHandler?) {
    mutableSettings.getAndUpdate { it.copy(readonlyHandler=readonlyHandler) }
  }

  override fun isWritable(hostDocument: Document): Boolean {
    if (mutableSettings.get().isReadOnly) {
      return false
    }
    for (guard in DocumentWriteAccessGuard.EP_NAME.extensionList) {
      val result = guard.isWritable(hostDocument)
      if (!result.isSuccess) {
        return false
      }
    }
    return true
  }

  override fun assertWriteAccess(hostDocument: Document) {
    if (isWriteAccessCheckEnabled) {
      val application = ApplicationManager.getApplication()
      // Document serves as a model for the Editor
      // hence, due to IJPL-184084, it must be updated on EDT
      ThreadingAssertions.assertEventDispatchThread()
      if (application != null) {
        application.assertWriteAccessAllowed()
        val file = FileDocumentManager.getInstance().getFile(hostDocument)
        if (file != null && file.isInLocalFileSystem) {
          (TransactionGuard.getInstance() as TransactionGuardImpl).assertWriteSafeEnvironment()
        }
      }
    }
  }

  override fun assertWritable(hostDocument: Document) {
    val settings = mutableSettings.get()
    if (settings.isReadOnly) {
      throw ReadOnlyModificationException(
        hostDocument,
        CoreBundle.message("attempt.to.modify.read.only.document.error.message"),
      )
    }
    for (guard in DocumentWriteAccessGuard.EP_NAME.extensionList) {
      val result = guard.isWritable(hostDocument)
      if (!result.isSuccess) {
        throw ReadOnlyModificationException(
          hostDocument,
          "${CoreBundle.message("attempt.to.modify.read.only.document.error.message")}: " +
          "guardClass=${guard.javaClass.getName()}, " +
          "failureReason=${result.failureReason}",
        )
      }
    }
  }

  override fun assertInsideCommand() {
    if (!isCommandCheckEnabled) {
      return
    }
    val commandProcessor = CommandProcessor.getInstance()
    if (!commandProcessor.isUndoTransparentActionInProgress() &&
        !commandProcessor.isCommandInProgress) {
      throw IncorrectOperationException(
        "Must not change document outside command or undo-transparent action. " +
        "See com.intellij.openapi.command.WriteCommandAction or com.intellij.openapi.command.CommandProcessor"
      )
    }
  }

  private fun suppressGuardCheck(suppress: Boolean, onlyWholeText: Boolean) {
    if (onlyWholeText) {
      mutableSettings.getAndUpdate { it.copy(isGuardCheckSuppressedWholeTextOnly=suppress) }
    } else {
      mutableSettings.getAndUpdate { it.copy(isGuardCheckSuppressed=suppress) }
    }
  }

  companion object {
    private val LOG: Logger = logger<DocumentSettingsImpl>()
  }
}
