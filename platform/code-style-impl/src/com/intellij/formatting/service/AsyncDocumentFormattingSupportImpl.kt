// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import com.intellij.CodeStyleBundle
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AsyncDocumentFormattingService.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile

@ApiStatus.Internal
class AsyncDocumentFormattingSupportImpl(private val service: AsyncDocumentFormattingService) : AsyncDocumentFormattingSupport {
  private val pendingRequests: ConcurrentHashMap<Document, FormattingRequestImpl> = ConcurrentHashMap()

  @Synchronized
  override fun formatDocument(
    document: Document,
    formattingRanges: List<TextRange>,
    formattingContext: FormattingContext,
    canChangeWhiteSpaceOnly: Boolean,
    quickFormat: Boolean
  ) {
    val currRequest = pendingRequests[document]
    val forceSync = true == document.getUserData(FORMAT_DOCUMENT_SYNCHRONOUSLY)
    if (currRequest != null) {
      if (!currRequest.cancel()) {
        LOG.warn("Pending request can't be cancelled")
        return
      }
    }
    prepareForFormatting(service, document, formattingContext)
    val formattingRequest = FormattingRequestImpl(formattingContext, document, formattingRanges,
                                                  canChangeWhiteSpaceOnly, quickFormat)
    val formattingTask = createFormattingTask(service, formattingRequest)
    if (formattingTask != null) {
      formattingRequest.setTask(formattingTask)
      pendingRequests[document] = formattingRequest
      if (forceSync || ApplicationManager.getApplication().isHeadlessEnvironment()) {
        runAsyncFormat(formattingRequest, null)
      }
      else {
        GlobalScope.launch(Dispatchers.IO) {
          runAsyncFormat(formattingRequest, formattingTask.isRunUnderProgress)
        }
      }
    }
  }

  private suspend fun runAsyncFormat(formattingRequest: FormattingRequestImpl, runUnderProgress: Boolean) {
    if (runUnderProgress) {
      withBackgroundProgress(formattingRequest.context.project,
                             CodeStyleBundle.message("async.formatting.service.running", getName(service)),
                             TaskCancellation.cancellable().withButtonText(CodeStyleBundle.message("async.formatting.service.cancel", getName(service)))) {
        coroutineToIndicator {
          runAsyncFormat(formattingRequest, it)
        }
      }
    }
    else {
      runAsyncFormat(formattingRequest, null)
    }
  }

  private fun runAsyncFormat(formattingRequest: FormattingRequestImpl, indicator: ProgressIndicator?) {
    try {
      formattingRequest.runTask(indicator)
    }
    finally {
      pendingRequests.remove(formattingRequest.document, formattingRequest)
    }
  }

  private enum class FormattingRequestState {
    NOT_STARTED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    COMPLETED,
    EXPIRED
  }

  private inner class FormattingRequestImpl(
    private val _context: FormattingContext,
    val document: Document,
    private val _ranges: List<TextRange>,
    private val _canChangeWhitespaceOnly: Boolean,
    private val _quickFormat: Boolean
  ) : AsyncFormattingRequest {
    private val initialModificationStamp = document.getModificationStamp()
    private val taskSemaphore = Semaphore(1)

    @Volatile
    private var task: FormattingTask? = null

    private var result: String? = null

    private val stateRef: AtomicReference<FormattingRequestState> = AtomicReference(
      FormattingRequestState.NOT_STARTED)

    fun cancel(): Boolean {
      val formattingTask = task
      if (formattingTask != null && stateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.CANCELLING)) {
        if (formattingTask.cancel()) {
          stateRef.set(FormattingRequestState.CANCELLED)
          taskSemaphore.release()
          return true
        }
      }
      return false
    }

    fun setTask(formattingTask: FormattingTask?) {
      task = formattingTask
    }

    fun runTask(indicator: ProgressIndicator?) {
      val task = task
      if (task != null && stateRef.compareAndSet(FormattingRequestState.NOT_STARTED, FormattingRequestState.RUNNING)) {
        try {
          taskSemaphore.acquire()
          task.run()
          var waitTime: Long = 0
          while (waitTime < getTimeout(service).seconds * 1000L) {
            if (taskSemaphore.tryAcquire(RETRY_PERIOD, TimeUnit.MILLISECONDS)) {
              taskSemaphore.release()
              break
            }
            indicator?.checkCanceled()
            waitTime += RETRY_PERIOD
          }
          if (stateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.EXPIRED)) {
            FormattingNotificationService.getInstance(_context.project).reportError(
              getNotificationGroupId(service),
              getTimeoutNotificationDisplayId(service), getName(service),
              CodeStyleBundle.message("async.formatting.service.timeout", getName(service),
                                      getTimeout(service).seconds.toString()),
              *getTimeoutActions(service, _context))
          }
          else if (result != null) {
            if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
              updateDocument(result!!)
            }
            else {
              ApplicationManager.getApplication().invokeLater {
                CommandProcessor.getInstance().runUndoTransparentAction {
                  try {
                    WriteAction.run<Throwable> {
                      updateDocument(result!!)
                    }
                  }
                  catch (throwable: Throwable) {
                    LOG.error(throwable)
                  }
                }
              }
            }
          }
        }
        catch (_: InterruptedException) {
          LOG.warn("Interrupted formatting thread.")
        }
      }
    }

    override fun getIOFile(): File? {
      val originalFile = _context.virtualFile
      val ext: String?
      val charset: Charset?
      if (originalFile != null) {
        if (originalFile.isInLocalFileSystem) {
          val localPath = originalFile.getFileSystem().getNioPath(originalFile)
          if (localPath != null) {
            return localPath.toFile()
          }
        }
        ext = originalFile.getExtension()
        charset = originalFile.getCharset()
      }
      else {
        ext = _context.containingFile.getFileType().getDefaultExtension()
        charset = EncodingManager.getInstance().getDefaultCharset()
      }
      try {
        val tempFile = FileUtilRt.createTempFile(TEMP_FILE_PREFIX, ".$ext", true)
        FileWriter(tempFile, charset).use { writer ->
          writer.write(documentText)
        }
        return tempFile
      }
      catch (e: IOException) {
        LOG.warn(e)
        return null
      }
    }

    fun updateDocument(newText: String) {
      if (!needToUpdate(service)) return
      if (document.getModificationStamp() > initialModificationStamp) {
        for (merger in DocumentMerger.EP_NAME.extensionList) {
          if (merger.updateDocument(this.document, newText)) break
        }
      }
      else {
        document.setText(newText)
      }
    }

    override fun onTextReady(updatedText: String?) {
      if (stateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        result = updatedText
        taskSemaphore.release()
      }
    }

    override fun onError(
      @NlsContexts.NotificationTitle title: @NlsContexts.NotificationTitle String,
      @NlsContexts.NotificationContent message: @NlsContexts.NotificationContent String,
      displayId: String?
    ) {
      if (stateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        taskSemaphore.release()
        FormattingNotificationService.getInstance(_context.project)
          .reportError(getNotificationGroupId(service), displayId, title, message)
      }
    }

    override fun onError(
      @NlsContexts.NotificationTitle title: @NlsContexts.NotificationTitle String,
      @NlsContexts.NotificationContent message: @NlsContexts.NotificationContent String,
      displayId: String?,
      offset: Int
    ) {
      if (stateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        taskSemaphore.release()
        FormattingNotificationService.getInstance(_context.project)
          .reportErrorAndNavigate(getNotificationGroupId(service), displayId, title, message, _context,
                                  offset)
      }
    }

    override fun getDocumentText(): String = document.text
    override fun getFormattingRanges(): List<TextRange> = _ranges
    override fun canChangeWhitespaceOnly(): Boolean = _canChangeWhitespaceOnly
    override fun getContext(): FormattingContext = _context
    override fun isQuickFormat(): Boolean = _quickFormat
  }

  companion object {
    private val LOG = thisLogger()
  }
}

private const val TEMP_FILE_PREFIX = "ij-format-temp"
private const val RETRY_PERIOD = 1000L // milliseconds