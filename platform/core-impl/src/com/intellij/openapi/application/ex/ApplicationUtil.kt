// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.ex

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.*
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Ref
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.EdtInvocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.Result
import kotlin.time.Duration.Companion.milliseconds

object ApplicationUtil {
  val LOG = Logger.getInstance(ApplicationUtil::class.java)

  // throws exception if it can't grab read action right now
  @Throws(CannotRunReadActionException::class)
  @JvmStatic
  fun <T> tryRunReadAction(computable: Computable<T>): T {
    val result = Ref<T>()
    if (!(ApplicationManager.getApplication() as ApplicationEx).tryRunReadAction { result.set(computable.compute()) }) {
      throw CannotRunReadActionException.create()
    }
    return result.get()
  }

  /**
   * Allows interrupting a process which does not perform checkCancelled() calls by itself.
   * Note that the process may continue to run in the background indefinitely - so **avoid using this method unless absolutely needed**.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Throws(Exception::class)
  @JvmStatic
  @ApiStatus.Obsolete
  fun <T> runWithCheckCanceled(callable: Callable<out T>, indicator: ProgressIndicator): T {
    @Suppress("UsagesOfObsoleteApi")
    val task = ClientId.decorateCallable {
      var result: T? = null
      var error: Throwable? = null
      ProgressManager.getInstance().executeProcessUnderProgress(
        {
          try {
            result = callable.call()
          }
          catch (e: Throwable) {
            error = e
          }
        }, indicator)

      error?.let {
        throw it
      }

      @Suppress("UNCHECKED_CAST")
      result as T
    }

    @Suppress("UsagesOfObsoleteApi", "RedundantSuppression")
    val deferred = (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().async(Dispatchers.IO) {
      task.call()
    }

    @Suppress("SSBasedInspection")
    return runBlocking {
      while (true) {
        select<Result<T>?> {
          deferred.onAwait {
            Result.success(it)
          }

          onTimeout(10.milliseconds) {
            try {
              indicator.checkCanceled()
              null
            }
            catch (e: ProcessCanceledException) {
              deferred.cancel()
              Result.failure(e)
            }
          }
        }?.let {
          return@runBlocking it.getOrThrow()
        }
      }

      @Suppress("UNREACHABLE_CODE")
      throw IllegalStateException("cannot be")
    }
  }

  /**
   * Waits for `future` to be complete, or the current thread's indicator to be canceled.
   * Note that `future` will not be cancelled by this method.<br></br>
   * See also [com.intellij.openapi.progress.util.ProgressIndicatorUtils.awaitWithCheckCanceled] which throws no checked exceptions.
   */
  @Throws(ExecutionException::class)
  @JvmStatic
  fun <T> runWithCheckCanceled(future: Future<T>, indicator: ProgressIndicator): T {
    while (true) {
      indicator.checkCanceled()

      try {
        return future.get(10, TimeUnit.MILLISECONDS)
      }
      catch (e: InterruptedException) {
        throw ProcessCanceledException(e)
      }
      catch (ignored: TimeoutException) {
      }
    }
  }

  @JvmStatic
  fun showDialogAfterWriteAction(runnable: Runnable) {
    val application = ApplicationManager.getApplication()
    if (application.isWriteAccessAllowed) {
      application.invokeLater(runnable)
    }
    else {
      runnable.run()
    }
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated(message = "Use withContext with proper dispatcher, or readAndWriteAction for read followed by write")
  @JvmStatic
  fun invokeLaterSomewhere(thread: EdtReplacementThread, modalityState: ModalityState, r: Runnable) {
    when (thread) {
      EdtReplacementThread.EDT -> SwingUtilities.invokeLater(r)
      EdtReplacementThread.WT -> ApplicationManager.getApplication().invokeLaterOnWriteThread(r, modalityState)
      EdtReplacementThread.EDT_WITH_IW -> ApplicationManager.getApplication().invokeLater(r, modalityState)
    }
  }

  @Deprecated(message = "Use withContext with proper dispatcher, or readAndWriteAction for read followed by write")
  @JvmStatic
  fun invokeAndWaitSomewhere(thread: EdtReplacementThread, modalityState: ModalityState, r: Runnable) {
    when (thread) {
      EdtReplacementThread.EDT -> {
        if (!SwingUtilities.isEventDispatchThread() && ApplicationManager.getApplication().isWriteIntentLockAcquired) {
          LOG.error("Can't invokeAndWait from WT to EDT: probably leads to deadlock")
        }
        EdtInvocationManager.invokeAndWaitIfNeeded(r)
      }
      EdtReplacementThread.WT -> if (ApplicationManager.getApplication().isWriteIntentLockAcquired) {
        r.run()
      }
      else if (SwingUtilities.isEventDispatchThread()) {
        LOG.error("Can't invokeAndWait from EDT to WT")
      }
      else {
        val s = Semaphore(1)
        val throwable = AtomicReference<Throwable?>()
        ApplicationManager.getApplication().invokeLaterOnWriteThread({
                                                                       try {
                                                                         r.run()
                                                                       }
                                                                       catch (t: Throwable) {
                                                                         throwable.set(t)
                                                                       }
                                                                       finally {
                                                                         s.up()
                                                                       }
                                                                     }, modalityState)
        s.waitFor()

        if (throwable.get() != null) {
          ExceptionUtil.rethrow(throwable.get())
        }
      }
      EdtReplacementThread.EDT_WITH_IW -> {
        if (!SwingUtilities.isEventDispatchThread() && ApplicationManager.getApplication().isWriteIntentLockAcquired) {
          LOG.error("Can't invokeAndWait from WT to EDT: probably leads to deadlock")
        }
        ApplicationManager.getApplication().invokeAndWait(r, modalityState)
      }
    }
  }

  class CannotRunReadActionException : ProcessCanceledException() {
    // When ForkJoinTask joins task which was exceptionally completed from the other thread,
    // it tries to re-create that exception (by reflection) and sets its cause to the original exception.
    // That horrible hack causes all sorts of confusion when we try to analyze the exception cause, e.g., in GlobalInspectionContextImpl.inspectFile().
    // To prevent creation of unneeded wrapped exception, we restrict constructor visibility to private so that stupid ForkJoinTask has no choice
    // but to use the original exception. (see ForkJoinTask.getThrowableException())
    companion object {
      @JvmStatic
      fun create(): CannotRunReadActionException {
        return CannotRunReadActionException()
      }
    }
  }
}