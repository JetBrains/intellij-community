// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.util.ExceptionUtil
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.UnindexedFilesUpdater
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.kind.SdkOrigin
import com.intellij.util.indexing.roots.kind.SyntheticLibraryOrigin
import com.intellij.util.indexing.roots.origin.ExternalEntityOrigin
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Internal
object FilesScanExecutor {
  private val LOG = Logger.getInstance(FilesScanExecutor::class.java)
  private val THREAD_COUNT = (UnindexedFilesUpdater.getNumberOfScanningThreads() - 1).coerceAtLeast(1)
  private val ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Scanning", THREAD_COUNT)

  @JvmStatic
  fun runOnAllThreads(runnable: Runnable) {
    val progress = ProgressIndicatorProvider.getGlobalProgressIndicator()
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      checkNotNull(progress) { "progress indicator is required" }
    }
    val results = ArrayList<Future<*>>()
    for (i in 0 until THREAD_COUNT) {
      results.add(ourExecutor.submit { ProgressManager.getInstance().runProcess(runnable, ProgressWrapper.wrap(progress)) })
    }

    // put the current thread to work too so the total thread count is `getNumberOfScanningThreads`
    // and avoid thread starvation due to a recursive `runOnAllThreads` invocation
    runnable.run()
    for (result in results) {
      // complete the future to avoid waiting for it forever if `ourExecutor` is fully booked
      (result as FutureTask<*>).run()
      ProgressIndicatorUtils.awaitWithCheckCanceled(result)
    }
  }

  @JvmStatic
  fun <T: Any> processOnAllThreadsInReadActionWithRetries(deque: ConcurrentLinkedDeque<T>, consumer: (T) -> Boolean): Boolean {
    return doProcessOnAllThreadsInReadAction(deque, consumer, true)
  }

  @JvmStatic
  fun <T: Any> processOnAllThreadsInReadActionNoRetries(deque: ConcurrentLinkedDeque<T>, consumer: (T) -> Boolean): Boolean {
    return doProcessOnAllThreadsInReadAction(deque, consumer, false)
  }

  private fun <T: Any> doProcessOnAllThreadsInReadAction(deque: ConcurrentLinkedDeque<T>,
                                                         consumer: (T) -> Boolean,
                                                         retryCanceled: Boolean): Boolean {
    val application = ApplicationManager.getApplication() as ApplicationEx
    return processOnAllThreads(deque) { o ->
      if (application.isReadAccessAllowed) {
        return@processOnAllThreads consumer(o)
      }

      var result = true
      val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
      if (!ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(
          { result = consumer(o) },
          indicator?.let { SensitiveProgressWrapper(it) }
        )) {
        throw if (retryCanceled) ProcessCanceledException() else StopWorker()
      }
      result
    }
  }

  private fun <T: Any> processOnAllThreads(deque: ConcurrentLinkedDeque<T>, processor: (T) -> Boolean): Boolean {
    ProgressManager.checkCanceled()
    if (deque.isEmpty()) {
      return true
    }
    val runnersCount = AtomicInteger()
    val idleCount = AtomicInteger()
    val error = AtomicReference<Throwable?>()
    val stopped = AtomicBoolean()
    val exited = AtomicBoolean()
    runOnAllThreads {
      runnersCount.incrementAndGet()
      var idle = false
      while (!stopped.get()) {
        ProgressManager.checkCanceled()
        if (deque.peek() == null) {
          if (!idle) {
            idle = true
            idleCount.incrementAndGet()
          }
        }
        else if (idle) {
          idle = false
          idleCount.decrementAndGet()
        }
        if (idle) {
          if (idleCount.get() == runnersCount.get() && deque.isEmpty()) break
          TimeoutUtil.sleep(1L)
          continue
        }
        val item = deque.poll() ?: continue
        try {
          if (!processor(item)) {
            stopped.set(true)
          }
          if (exited.get() && !stopped.get()) {
            throw AssertionError("early exit")
          }
        }
        catch (ex: StopWorker) {
          deque.addFirst(item)
          runnersCount.decrementAndGet()
          return@runOnAllThreads
        }
        catch (ex: ProcessCanceledException) {
          deque.addFirst(item)
        }
        catch (ex: Throwable) {
          error.compareAndSet(null, ex)
        }
      }
      exited.set(true)
      if (!deque.isEmpty() && !stopped.get()) {
        throw AssertionError("early exit")
      }
    }
    ExceptionUtil.rethrowAllAsUnchecked(error.get())
    return !stopped.get()
  }

  private fun isLibOrigin(origin: IndexableSetOrigin): Boolean {
    return origin is LibraryOrigin ||
           origin is SyntheticLibraryOrigin ||
           origin is SdkOrigin ||
           origin is ExternalEntityOrigin
  }

  @JvmStatic
  fun processFilesInScope(includingBinary: Boolean,
                          scope: GlobalSearchScope,
                          idFilter: IdFilter?,
                          processor: Processor<in VirtualFile?>): Boolean {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val project = scope.project ?: return true
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val searchInLibs = scope.isSearchInLibraries
    val deque = ConcurrentLinkedDeque<Any>()
    if (scope is VirtualFileEnumeration) {
      ContainerUtil.addAll<VirtualFile>(deque, FileBasedIndexEx.toFileIterable((scope as VirtualFileEnumeration).asArray()))
    }
    else {
      deque.addAll((FileBasedIndex.getInstance() as FileBasedIndexEx).getIndexableFilesProviders(project))
    }
    val skippedCount = AtomicInteger()
    val processedCount = AtomicInteger()
    val visitedFiles = ConcurrentBitSet.create(deque.size)
    val fileFilter = VirtualFileFilter { file: VirtualFile ->
      val fileId = FileBasedIndex.getFileId(file)
      if (visitedFiles.set(fileId)) return@VirtualFileFilter false
      val result = (idFilter == null || idFilter.containsFileId(fileId)) &&
                   !fileIndex.isExcluded(file) &&
                   scope.contains(file) &&
                   (includingBinary || file.isDirectory || !file.fileType.isBinary)
      if (!result) skippedCount.incrementAndGet()
      result
    }

    val consumer = l@ { obj: Any ->
      ProgressManager.checkCanceled()
      when (obj) {
        is IndexableFilesIterator -> {
          val origin = obj.origin
          if (!searchInLibs && isLibOrigin(origin)) return@l true
          obj.iterateFiles(project, { file: VirtualFile ->
            if (file.isDirectory) return@iterateFiles true
            deque.add(file)
            true
          }, fileFilter)
        }
        is VirtualFile -> {
          processedCount.incrementAndGet()
          if (!obj.isValid) {
            return@l true
          }
          return@l processor.process(obj)
        }
        else -> {
          throw AssertionError("unknown item: $obj")
        }
      }
      true
    }
    val start = System.nanoTime()
    val result = processOnAllThreadsInReadActionNoRetries(deque, consumer)
    if (LOG.isDebugEnabled) {
      LOG.debug("${processedCount.get()} files processed (${skippedCount.get()} skipped) in ${TimeoutUtil.getDurationMillis(start)} ms")
    }
    return result
  }
}

private class StopWorker : ProcessCanceledException()