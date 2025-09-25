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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.encoding.EncodingManager
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile

@ApiStatus.Internal
class AsyncDocumentFormattingSupportImpl(private val myService: AsyncDocumentFormattingService) : AsyncDocumentFormattingSupport {
  private val myPendingRequests: MutableList<FormattingRequestImpl> = Collections.synchronizedList(ArrayList())

  @Synchronized
  override fun formatDocument(
    document: Document,
    formattingRanges: List<TextRange>,
    formattingContext: FormattingContext,
    canChangeWhiteSpaceOnly: Boolean,
    quickFormat: Boolean
  ) {
    val currRequest = findPendingRequest(document)
    val forceSync = true == document.getUserData(FORMAT_DOCUMENT_SYNCHRONOUSLY)
    if (currRequest != null) {
      if (!currRequest.cancel()) {
        LOG.warn("Pending request can't be cancelled")
        return
      }
    }
    prepareForFormatting(myService, document, formattingContext)
    val formattingRequest = FormattingRequestImpl(formattingContext, document, formattingRanges,
                                                  canChangeWhiteSpaceOnly, quickFormat)
    val formattingTask = createFormattingTask(myService, formattingRequest)
    if (formattingTask != null) {
      formattingRequest.setTask(formattingTask)
      myPendingRequests.add(formattingRequest)
      if (forceSync || ApplicationManager.getApplication().isHeadlessEnvironment()) {
        runAsyncFormat(formattingRequest, null)
      }
      else {
        if (formattingTask.isRunUnderProgress) {
          this.FormattingProgressTask(formattingRequest)
            .setCancelText(CodeStyleBundle.message("async.formatting.service.cancel", getName(myService)))
            .queue()
        }
        else {
          ApplicationManager.getApplication().executeOnPooledThread { runAsyncFormat(formattingRequest, null) }
        }
      }
    }
  }

  private fun findPendingRequest(document: Document): FormattingRequestImpl? {
    synchronized(myPendingRequests) {
      return myPendingRequests.find { it.document === document }
    }
  }

  private fun runAsyncFormat(formattingRequest: FormattingRequestImpl, indicator: ProgressIndicator?) {
    try {
      formattingRequest.runTask(indicator)
    }
    finally {
      myPendingRequests.remove(formattingRequest)
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

  private inner class FormattingProgressTask(private val myRequest: FormattingRequestImpl) : Task.Backgroundable(
    myRequest.context.project,
    CodeStyleBundle.message("async.formatting.service.running", getName(myService)), true) {
    override fun run(indicator: ProgressIndicator) {
      indicator.setIndeterminate(false)
      indicator.setFraction(0.0)
      runAsyncFormat(myRequest, indicator)
      indicator.setFraction(1.0)
    }

    override fun onCancel() {
      myRequest.cancel()
    }
  }

  private inner class FormattingRequestImpl(
    private val myContext: FormattingContext,
    val document: Document,
    private val myRanges: List<TextRange>,
    private val myCanChangeWhitespaceOnly: Boolean,
    private val myQuickFormat: Boolean
  ) : AsyncFormattingRequest {
    private val myInitialModificationStamp = document.getModificationStamp()
    private val myTaskSemaphore = Semaphore(1)

    @Volatile
    private var myTask: FormattingTask? = null

    private var myResult: String? = null

    private val myStateRef: AtomicReference<FormattingRequestState> = AtomicReference(
      FormattingRequestState.NOT_STARTED)

    override fun getIOFile(): File? {
      val originalFile = myContext.virtualFile
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
        ext = myContext.containingFile.getFileType().getDefaultExtension()
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

    override fun getDocumentText(): String {
      return document.text
    }

    fun cancel(): Boolean {
      val formattingTask = myTask
      if (formattingTask != null && myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.CANCELLING)) {
        if (formattingTask.cancel()) {
          myStateRef.set(FormattingRequestState.CANCELLED)
          myTaskSemaphore.release()
          return true
        }
      }
      return false
    }

    override fun getFormattingRanges(): List<TextRange> {
      return myRanges
    }

    override fun canChangeWhitespaceOnly(): Boolean {
      return myCanChangeWhitespaceOnly
    }

    override fun getContext(): FormattingContext {
      return myContext
    }

    fun setTask(formattingTask: FormattingTask?) {
      myTask = formattingTask
    }

    fun runTask(indicator: ProgressIndicator?) {
      val task = myTask
      if (task != null && myStateRef.compareAndSet(FormattingRequestState.NOT_STARTED, FormattingRequestState.RUNNING)) {
        try {
          myTaskSemaphore.acquire()
          task.run()
          var waitTime: Long = 0
          while (waitTime < getTimeout(myService).seconds * 1000L) {
            if (myTaskSemaphore.tryAcquire(RETRY_PERIOD, TimeUnit.MILLISECONDS)) {
              myTaskSemaphore.release()
              break
            }
            indicator?.checkCanceled()
            waitTime += RETRY_PERIOD
          }
          if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.EXPIRED)) {
            FormattingNotificationService.getInstance(myContext.project).reportError(
              getNotificationGroupId(myService),
              getTimeoutNotificationDisplayId(myService), getName(myService),
              CodeStyleBundle.message("async.formatting.service.timeout", getName(myService),
                                      getTimeout(myService).seconds.toString()),
              *getTimeoutActions(myService, myContext))
          }
          else if (myResult != null) {
            if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
              updateDocument(myResult!!)
            }
            else {
              ApplicationManager.getApplication().invokeLater {
                CommandProcessor.getInstance().runUndoTransparentAction {
                  try {
                    WriteAction.run<Throwable> {
                      updateDocument(myResult!!)
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

    fun updateDocument(newText: String) {
      if (!needToUpdate(myService)) return
      if (document.getModificationStamp() > myInitialModificationStamp) {
        for (merger in DocumentMerger.EP_NAME.extensionList) {
          if (merger.updateDocument(this.document, newText)) break
        }
      }
      else {
        document.setText(newText)
      }
    }

    override fun isQuickFormat(): Boolean {
      return myQuickFormat
    }

    override fun onTextReady(updatedText: String?) {
      if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        myResult = updatedText
        myTaskSemaphore.release()
      }
    }

    override fun onError(
      @NlsContexts.NotificationTitle title: @NlsContexts.NotificationTitle String,
      @NlsContexts.NotificationContent message: @NlsContexts.NotificationContent String,
      displayId: String?
    ) {
      if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        myTaskSemaphore.release()
        FormattingNotificationService.getInstance(myContext.project)
          .reportError(getNotificationGroupId(myService), displayId, title, message)
      }
    }

    override fun onError(
      @NlsContexts.NotificationTitle title: @NlsContexts.NotificationTitle String,
      @NlsContexts.NotificationContent message: @NlsContexts.NotificationContent String,
      displayId: String?,
      offset: Int
    ) {
      if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        myTaskSemaphore.release()
        FormattingNotificationService.getInstance(myContext.project)
          .reportErrorAndNavigate(getNotificationGroupId(myService), displayId, title, message, myContext,
                                  offset)
      }
    }
  }

  companion object {
    private val LOG = thisLogger()
  }
}

private const val TEMP_FILE_PREFIX = "ij-format-temp"
private const val RETRY_PERIOD = 1000L // milliseconds