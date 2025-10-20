// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import com.intellij.CodeStyleBundle
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AsyncDocumentFormattingService.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.progress.withCurrentThreadCoroutineScopeBlocking
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.SystemProperties
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

@ApiStatus.Internal
class AsyncDocumentFormattingSupportImpl(private val service: AsyncDocumentFormattingService) : AsyncDocumentFormattingSupport {
  private val pendingRequests: ConcurrentHashMap<Document, FormattingRequestImpl> = ConcurrentHashMap()

  @Synchronized
  override fun formatDocument(
    document: Document,
    formattingRanges: List<TextRange>,
    formattingContext: FormattingContext,
    canChangeWhiteSpaceOnly: Boolean,
    quickFormat: Boolean,
  ) {
    val currRequest = pendingRequests[document]
    val isSync = isSyncFormat(document)
    if (currRequest != null) {
      if (!currRequest.cancel()) {
        LOG.warn("Pending request can't be cancelled")
        return
      }
    }
    prepareForFormatting(service, document, formattingContext)
    val formattingRequest = FormattingRequestImpl(formattingContext, document, formattingRanges,
                                                  canChangeWhiteSpaceOnly, quickFormat, isSync)
    val formattingTask = createFormattingTask(service, formattingRequest)
    if (formattingTask != null) {
      formattingRequest.setTask(formattingTask)
      pendingRequests[document] = formattingRequest
      if (isSync) {
        runAsyncFormatBlocking(formattingRequest)
      }
      else {
        withCurrentThreadCoroutineScopeBlocking {
          // UNDISPATCHED start: formattingTask.run must be called even if the currentThreadCoroutineScope is already canceled
          currentThreadCoroutineScope().launch(start = CoroutineStart.UNDISPATCHED) {
            runAsyncFormat(formattingRequest)
          }
        }
      }
    }
  }

  private fun isSyncFormat(document: Document): Boolean {
    val forceSync = isForceSyncFormat(document)
    val isHeadless = ApplicationManager.getApplication().isHeadlessEnvironment
    val isIgnoreHeadless = SystemProperties.getBooleanProperty("intellij.async.formatting.ignoreHeadless", false)
    return forceSync || (isHeadless && !isIgnoreHeadless)
  }

  private fun isForceSyncFormat(document: Document): Boolean = document.getUserData(FORMAT_DOCUMENT_SYNCHRONOUSLY) == true

  @Suppress("RAW_RUN_BLOCKING")
  private fun runAsyncFormatBlocking(request: FormattingRequestImpl) {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
      // raw runBlocking instead of runWithModalProgressBlocking:
      // FormattingService.format* is usually called in EDT WA (i.e., in a way that permits modifying the document/PSI)
      //  1. in headless mode/unit tests, we do not want to log error
      //  2. in non-headless mode, forced sync formating should only be used with background documents
      runBlocking {
        runAsyncFormat(request)
      }
    }
    else {
      runBlockingMaybeCancellable {
        runAsyncFormat(request)
      }
    }
  }

  private suspend fun runAsyncFormat(formattingRequest: FormattingRequestImpl) {
    try {
      formattingRequest.runAndAwaitTask()
    }
    finally {
      pendingRequests.remove(formattingRequest.document, formattingRequest)
    }
  }

  private inner class FormattingRequestImpl(
    private val _context: FormattingContext,
    val document: Document,
    private val _ranges: List<TextRange>,
    private val _canChangeWhitespaceOnly: Boolean,
    private val _quickFormat: Boolean,
    private val isSync: Boolean,
  ) : AsyncFormattingRequest {
    private val initialModificationStamp = document.getModificationStamp()

    @Volatile
    private var task: FormattingTask? = null

    private val taskStarted = CompletableDeferred<Unit>()
    private val taskResult = CompletableDeferred<String?>()

    fun cancel(): Boolean {
      if (!taskStarted.isCompleted) return false
      // for our purpose, taskResult.cancel is equivalent, but we need the CAS semantics
      if (taskResult.completeExceptionally(CancellationException())) {
        val formattingTask = checkNotNull(task)
        return formattingTask.cancel()
      }
      return false
    }

    fun setTask(formattingTask: FormattingTask) {
      task = formattingTask
    }

    private suspend fun underProgressIfNeeded(isNeeded: Boolean, block: () -> Unit) {
      if (isNeeded) {
        withBackgroundProgress(context.project,
                               CodeStyleBundle.message("async.formatting.service.running", getName(service)),
                               TaskCancellation.cancellable()
                                 .withButtonText(CodeStyleBundle.message("async.formatting.service.cancel", getName(service)))) {
          coroutineToIndicator {
            block()
          }
        }
      }
      else {
        block()
      }
    }

    private fun notifyExpired() {
      FormattingNotificationService.getInstance(_context.project).reportError(
        getNotificationGroupId(service),
        getTimeoutNotificationDisplayId(service), getName(service),
        CodeStyleBundle.message("async.formatting.service.timeout", getName(service),
                                getTimeout(service).seconds.toString()),
        *getTimeoutActions(service, _context)
      )
    }

    @OptIn(DelicateCoroutinesApi::class) // GlobalScope, CoroutineStart.ATOMIC
    suspend fun runAndAwaitTask() = coroutineScope {
      val task = checkNotNull(task)
      val taskDispatcher = if (isSync) {
        // Keep sync tasks in the runBlocking event loop; FormattingTask.run must be called from the calling thread in sync mode
        requireNotNull(coroutineContext[CoroutineDispatcher])
      }
      else {
        Dispatchers.IO
      }
      // There is an existing contract that a task that was already created is also started.
      // GlobalScope is used here to prevent cancellation before that can happen.
      val taskJob = GlobalScope.launch(taskDispatcher) {
        try {
          underProgressIfNeeded(task.isRunUnderProgress) {
            taskStarted.complete(Unit)
            task.run()
          }
        } catch (t: Throwable) {
          // unblock waiter if failure happened before `taskStarted.complete(Unit)` could run
          taskStarted.completeExceptionally(t)
          throw t
        }
      }
      // ATOMIC start is used here to ensure taskJob is properly cleaned up
      launch(Dispatchers.Default, start = CoroutineStart.ATOMIC) {
        val timeout = getTimeout(service).toNanos().nanoseconds
        val markStarted = TimeSource.Monotonic.markNow()
        try {
          withContext(NonCancellable) {
            taskStarted.await()
          }
          val formattedText = withTimeout(timeout) {
            taskResult.await()
          } ?: return@launch
          if (isSync) {
            withContext(taskDispatcher) {
              updateDocument(formattedText)
            }
          }
          else {
            withContext(Dispatchers.EDT) {
              CommandProcessor.getInstance().runUndoTransparentAction {
                WriteAction.run<Throwable> {
                  updateDocument(formattedText)
                }
              }
            }
          }
        }
        catch (t: Throwable) {
          if (t !is CancellationException) {
            LOG.error(t)
          }
          this@FormattingRequestImpl.cancel()
          taskJob.cancel()
          throw t
        }
        finally {
          val elapsed = markStarted.elapsedNow()
          val remainingToTimeout = timeout - elapsed
          withContext(NonCancellable) {
            withTimeoutOrNull(remainingToTimeout) {
              taskJob.join()
            } ?: notifyExpired()
          }
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
      taskResult.complete(updatedText)
    }

    override fun onError(title: @NlsContexts.NotificationTitle String, message: @NlsContexts.NotificationContent String) {
      onError(title, message, null)
    }

    override fun onError(
      @NlsContexts.NotificationTitle title: @NlsContexts.NotificationTitle String,
      @NlsContexts.NotificationContent message: @NlsContexts.NotificationContent String,
      displayId: String?,
    ) {
      if (taskResult.complete(null)) {
        FormattingNotificationService.getInstance(_context.project)
          .reportError(getNotificationGroupId(service), displayId, title, message)
      }
    }

    override fun onError(title: @NlsContexts.NotificationTitle String, message: @NlsContexts.NotificationContent String, offset: Int) {
      onError(title, message, null, offset)
    }

    override fun onError(
      @NlsContexts.NotificationTitle title: @NlsContexts.NotificationTitle String,
      @NlsContexts.NotificationContent message: @NlsContexts.NotificationContent String,
      displayId: String?,
      offset: Int,
    ) {
      if (taskResult.complete(null)) {
        FormattingNotificationService.getInstance(_context.project)
          .reportErrorAndNavigate(getNotificationGroupId(service), displayId, title, message, _context, offset)
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