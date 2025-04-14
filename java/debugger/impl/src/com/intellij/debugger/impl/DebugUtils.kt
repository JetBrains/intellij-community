// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.DebugProcessEvents
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.*
import com.sun.jdi.event.ClassPrepareEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * This exception is thrown when a required helper class is not available during an evaluation process.
 */
class HelperClassNotAvailableException(message: String) : EvaluateException(message)

/**
 * Exception thrown when a specified method cannot be found during evaluation.
 */
class MethodNotFoundException(message: String) : EvaluateException(message)

internal inline fun <T, E : Exception> suppressExceptions(
  defaultValue: T?,
  rethrow: Class<E>? = null,
  supplier: () -> T?,
): T? = try {
  supplier()
}
catch (e: Throwable) {
  when (e) {
    is ProcessCanceledException, is CancellationException, is VMDisconnectedException, is ObjectCollectedException -> {
      throw e
    }
    is InternalException -> {
      fileLogger().info(e)
    }
    is Exception, is AssertionError -> {
      if (rethrow != null && rethrow.isInstance(e)) {
        throw e
      }
      fileLogger().error(e)
    }
    else -> throw e
  }
  defaultValue
}

// do not catch VMDisconnectedException
inline fun <T : Any, R> computeSafeIfAny(ep: ExtensionPointName<T>, processor: (T) -> R?): R? =
  ep.extensionList.firstNotNullOfOrNull { t ->
    try {
      processor(t)
    }
    catch (e: Exception) {
      if (e is ProcessCanceledException || e is VMDisconnectedException || e is CancellationException) {
        throw e
      }
      fileLogger().error(e)
      null
    }
  }

// TODO: move into VirtualMachineProxyImpl when converted to kotlin
fun preloadAllClasses(vm: VirtualMachine) {
  DebuggerManagerThreadImpl.assertIsManagerThread()
  val allClasses = DebuggerUtilsAsync.allCLasses(vm)
  if (!Registry.`is`("debugger.preload.types.hierarchy", true) || DebuggerUtils.isAndroidVM(vm)) return

  val channel = Channel<ReferenceType>(capacity = Channel.UNLIMITED)
  try {
    DebugProcessEvents.enableNonSuspendingRequest(vm.eventRequestManager().createClassPrepareRequest()) { event ->
      channel.trySend((event as ClassPrepareEvent).referenceType())
    }
  }
  catch (_: UnsupportedOperationException) {
  }

  val managerThread = InvokeThread.currentThread() as DebuggerManagerThreadImpl
  managerThread.coroutineScope.launch {
    launch {
      val classes = allClasses.await()
      for (type in classes) {
        channel.trySend(type)
      }
    }
    channel.consumeEach { type ->
      managerThread.schedule(PrioritizedTask.Priority.LOWEST) {
        try {
          DebuggerUtilsAsync.supertypes(type)
        }
        catch (_: ObjectCollectedException) {
        }
      }
    }
  }
}
