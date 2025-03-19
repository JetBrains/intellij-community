// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.platform.instanceContainer.CycleInitializationException
import com.intellij.util.findCycle
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import kotlin.coroutines.*

internal abstract class LazyInstanceHolder(
  parentScope: CoroutineScope,
  initializer: InstanceInitializer,
) : InstanceHolder {

  private companion object {
    val stateHandle: VarHandle = MethodHandles
      .privateLookupIn(LazyInstanceHolder::class.java, MethodHandles.lookup())
      .findVarHandle(LazyInstanceHolder::class.java, "_state", Any::class.java)
  }

  private class Initial(val parentScope: CoroutineScope, val initializer: InstanceInitializer)
  private data class InProgress(val initializer: InstanceInitializer, val waiters: PersistentSet<Continuation<Any>>)
  private class CannotLoadClass(val instanceClassName: String, val classLoadingError: Throwable)
  private class CannotInitialize(val instanceClass: Class<*>, val initializationError: Throwable)

  private var _state: Any = Initial(parentScope, initializer)
  private fun state(): Any = stateHandle.getVolatile(this)

  override fun toString(): String {
    return when (val state = _state) {
      is Initial -> "uninitialized '${state.initializer.instanceClassName}'"
      is InProgress -> "initializing '${state.initializer.instanceClassName}'"
      is CannotLoadClass -> "class loading error ${state.classLoadingError.message}"
      is CannotInitialize -> "initialization error ${state.initializationError.message} '${state.instanceClass.name}'"
      else -> "initialized ${state.javaClass.name}"
    }
  }

  override fun instanceClassName(): String {
    return when (val state = _state) {
      is Initial -> state.initializer.instanceClassName
      is InProgress -> state.initializer.instanceClassName
      is CannotLoadClass -> state.instanceClassName
      is CannotInitialize -> state.instanceClass.name
      else -> state.javaClass.name
    }
  }

  override fun instanceClass(): Class<*> {
    return when (val state = _state) {
      is Initial -> state.initializer.loadInstanceClass(keyClass = null)
      is InProgress -> state.initializer.loadInstanceClass(keyClass = null)
      is CannotLoadClass -> throw state.classLoadingError
      is CannotInitialize -> state.instanceClass
      else -> state.javaClass
    }
  }

  override fun tryGetInstance(): Any? {
    return when (val state = _state) {
      is Initial -> null
      is InProgress -> null
      is CannotLoadClass -> throw state.classLoadingError
      is CannotInitialize -> throw state.initializationError
      else -> state
    }
  }

  override suspend fun getInstanceIfRequested(): Any? {
    return when (val state = state()) {
      is Initial -> null
      is InProgress -> tryAwait(state)
      is CannotLoadClass -> throw state.classLoadingError
      is CannotInitialize -> throw state.initializationError
      else -> state
    }
  }

  override suspend fun getInstance(keyClass: Class<*>?): Any {
    return getInstance(keyClass, useCallerContext = false)
  }

  override suspend fun getInstanceInCallerContext(keyClass: Class<*>?): Any {
    return getInstance(keyClass, useCallerContext = true)
  }

  private suspend fun getInstance(keyClass: Class<*>?, useCallerContext: Boolean): Any {
    tryGetInstance()?.let {
      return it
    }
    return when (val state = state()) {
      is Initial -> tryInitialize(state, keyClass, useCallerContext)
      is InProgress -> tryAwait(state)
      is CannotLoadClass -> throw state.classLoadingError
      is CannotInitialize -> throw state.initializationError
      else -> state
    }
  }

  private suspend fun tryInitialize(state: Initial, keyClass: Class<*>?, useCallerContext: Boolean): Any {
    val initializer = state.initializer
    val newState = InProgress(initializer, persistentHashSetOf())
    val witness = stateHandle.compareAndExchange(this, state, newState)
    if (witness !== state) {
      when (witness) {
        is Initial -> error("Unexpected state")
        is InProgress -> return tryAwait(witness)
        is CannotLoadClass -> throw witness.classLoadingError
        is CannotInitialize -> throw witness.initializationError
        else -> return witness
      }
    }
    val instanceClass = try {
      initializer.loadInstanceClass(keyClass = keyClass)
    }
    catch (t: Throwable) {
      complete(finalState = CannotLoadClass(initializer.instanceClassName, t))
      throw t
    }

    val callerCtx = if (useCallerContext) {
      // for example, inside runBlocking its event loop will be used for initialization,
      // and/or context modality state will be used
      currentCoroutineContext().minusKey(Job)
    }
    else {
      EmptyCoroutineContext
    }
    return suspendCancellableCoroutine { waiter ->
      tryAwait(newState, waiter)
      // publish waiter before `initialize()` because it's undispatched
      initialize(state.parentScope, callerCtx, initializer, instanceClass)
    }
  }

  private fun initialize(
    parentScope: CoroutineScope,
    callerCtx: CoroutineContext,
    initializer: InstanceInitializer,
    instanceClass: Class<*>,
  ) {
    parentScope.launch(
      context = callerCtx + CurrentlyInitializingInstance(this) + CoroutineName("${initializer.instanceClassName} init"),
      start = CoroutineStart.UNDISPATCHED,
    ) {
      // TODO Initialization of services happens in a child coroutine of container scope (this coroutine)
      //  => cancellation of container scope cancels currently initializing instances as well as
      //  it prevents initialization of the new instances.
      //  - Some services (for example, `com.intellij.vcs.log.impl.VcsProjectLog.dropLogManager`)
      //    expect `project.messageBus` to work after cancellation of the service scope.
      //  - Some listeners (namely `EditorFactoryListener.editorReleased`) request other services during `startDispose()`.
      //  - We have a number of services, which request other services uninitialized during own disposal.
      //  Requesting an uninitialized instance during disposal of another instance is incorrect,
      //  but it's legacy, and we have to live with it for a while.
      //  To maintain the old behavior we run initialization in NonCancellable context:
      //  the instance will be initialized even if the container scope is already cancelled.
      withContext(NonCancellable) {
        try {
          complete(finalState = initializer.createInstance(parentScope, instanceClass))
        }
        catch (t: Throwable) {
          complete(finalState = CannotInitialize(instanceClass = instanceClass, t))
        }
      }
    }
  }

  private fun complete(finalState: Any) {
    val result = when (finalState) {
      is Initial,
      is InProgress -> error("Unexpected completion $finalState")
      is CannotLoadClass -> Result.failure(finalState.classLoadingError)
      is CannotInitialize -> Result.failure(finalState.initializationError)
      else -> Result.success(finalState)
    }
    val state = stateHandle.getAndSet(this, finalState)
    if (state !is InProgress) {
      error("Unexpected state $state")
    }
    state.waiters.forEach {
      it.resumeWith(result)
    }
  }

  private suspend fun tryAwait(state: InProgress): Any {
    return suspendCancellableCoroutine { waiter ->
      tryAwait(state, waiter)
    }
  }

  private fun tryAwait(lastSeenState: InProgress, waiter: CancellableContinuation<Any>) {
    var state: Any = lastSeenState
    while (true) {
      when (state) {
        is Initial -> {
          error("Unexpected state")
        }
        is InProgress -> {
          val newState = state.copy(waiters = state.waiters.add(waiter))
          val witness = stateHandle.compareAndExchange(this, state, newState)
          if (witness === state) {
            waiter.invokeOnCancellation {
              if (it != null) {
                waiterCancelled(waiter)
              }
            }
            checkCycle()
            return
          }
          else {
            state = witness // loop again
          }
        }
        is CannotLoadClass -> {
          waiter.resumeWithException(state.classLoadingError)
          return
        }
        is CannotInitialize -> {
          waiter.resumeWithException(state.initializationError)
          return
        }
        else -> {
          waiter.resume(state)
          return
        }
      }
    }
  }

  private fun checkCycle() {
    val cycle = findCycle(this) { holder ->
      val state = stateHandle.getVolatile(holder)
      if (state is InProgress) {
        state.waiters.mapNotNull {
          it.context[CurrentlyInitializingInstance]?.holder
        }
      }
      else {
        emptyList()
      }
    }
    if (cycle != null) {
      val classes = cycle.map { it.instanceClass().name }
      throw CycleInitializationException(classes.toString())
    }
  }

  private fun waiterCancelled(waiter: CancellableContinuation<Any>) {
    var state: Any = state()
    while (true) {
      when (state) {
        is Initial -> {
          error("Unexpected state")
        }
        is InProgress -> {
          val newState = state.copy(waiters = state.waiters.remove(waiter))
          val witness = stateHandle.compareAndExchange(this, state, newState)
          if (witness === state) {
            return
          }
          else {
            state = witness // loop again
          }
        }
        else -> {
          return
        }
      }
    }
  }
}

private class CurrentlyInitializingInstance(val holder: LazyInstanceHolder)
  : AbstractCoroutineContextElement(CurrentlyInitializingInstance), IntelliJContextElement {
  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
  companion object : CoroutineContext.Key<CurrentlyInitializingInstance>
}

internal class StaticInstanceHolder(scope: CoroutineScope, initializer: InstanceInitializer)
  : LazyInstanceHolder(scope, initializer)

/**
 * This class is separate from [StaticInstanceHolder] to differentiate them via `instanceof` later.
 * Another solution is to store a flag in a field.
 */
internal class DynamicInstanceHolder(scope: CoroutineScope, initializer: InstanceInitializer)
  : LazyInstanceHolder(scope, initializer)
