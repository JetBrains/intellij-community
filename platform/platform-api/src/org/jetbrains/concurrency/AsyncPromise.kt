/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.concurrency

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Getter
import com.intellij.util.Consumer
import com.intellij.util.Function
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val LOG = Logger.getInstance(AsyncPromise::class.java)

@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
private val OBSOLETE_ERROR = Promise.createError("Obsolete")

open class AsyncPromise<T> : Promise<T>(), Getter<T> {
  @Volatile private var done: Consumer<in T>? = null
  @Volatile private var rejected: Consumer<in Throwable>? = null

  private val state = AtomicReference(Promise.State.PENDING)

  // result object or error message
  @Volatile private var result: Any? = null

  override fun getState() = state.get()!!

  override fun done(done: Consumer<in T>): Promise<T> {
    if (isObsolete(done)) {
      return this
    }

    when (state.get()!!) {
      Promise.State.PENDING -> {
        this.done = setHandler(this.done, done, State.FULFILLED)
      }
      Promise.State.FULFILLED -> {
        @Suppress("UNCHECKED_CAST")
        done.consume(result as T?)
      }
      Promise.State.REJECTED -> {
      }
    }

    return this
  }

  override fun rejected(rejected: Consumer<Throwable>): Promise<T> {
    if (isObsolete(rejected)) {
      return this
    }

    when (state.get()!!) {
      Promise.State.PENDING -> {
        this.rejected = setHandler(this.rejected, rejected, State.REJECTED)
      }
      Promise.State.FULFILLED -> {
      }
      Promise.State.REJECTED -> {
        rejected.consume(result as Throwable?)
      }
    }

    return this
  }

  @Suppress("UNCHECKED_CAST")
  override fun get() = if (state.get() == Promise.State.FULFILLED) result as T? else null

  override fun <SUB_RESULT> then(fulfilled: Function<in T, out SUB_RESULT>): Promise<SUB_RESULT> {
    @Suppress("UNCHECKED_CAST")
    when (state.get()!!) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> return DonePromise<SUB_RESULT>(fulfilled.`fun`(result as T?))
      Promise.State.REJECTED -> return rejectedPromise(result as Throwable)
    }

    val promise = AsyncPromise<SUB_RESULT>()
    addHandlers(Consumer({ result ->
                           promise.catchError {
                             if (fulfilled is Obsolescent && fulfilled.isObsolete) {
                               promise.cancel()
                             }
                             else {
                               promise.setResult(fulfilled.`fun`(result))
                             }
                           }
                         }), Consumer({ promise.setError(it) }))
    return promise
  }

  override fun notify(child: AsyncPromise<in T>) {
    LOG.assertTrue(child !== this)

    when (state.get()!!) {
      Promise.State.PENDING -> {
        addHandlers(Consumer({ child.catchError { child.setResult(it) } }), Consumer({ child.setError(it) }))
      }
      Promise.State.FULFILLED -> {
        @Suppress("UNCHECKED_CAST")
        child.setResult(result as T)
      }
      Promise.State.REJECTED -> {
        child.setError((result as Throwable?)!!)
      }
    }
  }

  override fun <SUB_RESULT> thenAsync(fulfilled: Function<in T, Promise<SUB_RESULT>>): Promise<SUB_RESULT> {
    @Suppress("UNCHECKED_CAST")
    when (state.get()!!) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> return fulfilled.`fun`(result as T?)
      Promise.State.REJECTED -> return rejectedPromise(result as Throwable)
    }

    val promise = AsyncPromise<SUB_RESULT>()
    val rejectedHandler = Consumer<Throwable>({ promise.setError(it) })
    addHandlers(Consumer({
                           promise.catchError {
                             fulfilled.`fun`(it)
                                 .done { promise.catchError { promise.setResult(it) } }
                                 .rejected(rejectedHandler)
                           }
                         }), rejectedHandler)
    return promise
  }

  override fun processed(fulfilled: AsyncPromise<in T>): Promise<T> {
    when (state.get()!!) {
      Promise.State.PENDING -> {
        addHandlers(Consumer({ result -> fulfilled.catchError { fulfilled.setResult(result) } }), Consumer({ fulfilled.setError(it) }))
      }
      Promise.State.FULFILLED -> {
        @Suppress("UNCHECKED_CAST")
        fulfilled.setResult(result as T)
      }
      Promise.State.REJECTED -> {
        fulfilled.setError((result as Throwable?)!!)
      }
    }
    return this
  }

  private fun addHandlers(done: Consumer<T>, rejected: Consumer<Throwable>) {
    this.done = setHandler(this.done, done, State.FULFILLED)
    this.rejected = setHandler(this.rejected, rejected, State.REJECTED)
  }

  fun setResult(result: T?) {
    if (!state.compareAndSet(Promise.State.PENDING, Promise.State.FULFILLED)) {
      return
    }

    this.result = result

    val done = this.done
    clearHandlers()
    if (done != null && !isObsolete(done)) {
      done.consume(result)
    }
  }

  fun setError(error: String) = setError(Promise.createError(error))

  fun cancel() {
    setError(OBSOLETE_ERROR)
  }

  open fun setError(error: Throwable): Boolean {
    if (!state.compareAndSet(Promise.State.PENDING, Promise.State.REJECTED)) {
      return false
    }

    result = error

    val rejected = this.rejected
    clearHandlers()
    if (rejected == null) {
      Promise.logError(LOG, error)
    }
    else if (!isObsolete(rejected)) {
      rejected.consume(error)
    }
    return true
  }

  private fun clearHandlers() {
    done = null
    rejected = null
  }

  override fun processed(processed: Consumer<in T>): Promise<T> {
    done(processed)
    rejected { processed.consume(null) }
    return this
  }

  private fun <T> setHandler(oldConsumer: Consumer<in T>?, newConsumer: Consumer<in T>, targetState: State): Consumer<in T>? = when (oldConsumer) {
    null -> newConsumer
    is CompoundConsumer<*> -> {
      @Suppress("UNCHECKED_CAST")
      val compoundConsumer = oldConsumer as CompoundConsumer<T>
      synchronized(compoundConsumer) {
        compoundConsumer.consumers.let {
          if (it == null) {
            // clearHandlers was called - just execute newConsumer
            if (state.get() == targetState) {
              @Suppress("UNCHECKED_CAST")
              newConsumer.consume(result as T?)
            }
            return null
          }
          else {
            it.add(newConsumer)
            return compoundConsumer
          }
        }
      }
    }
    else -> CompoundConsumer(oldConsumer, newConsumer)
  }
}

private class CompoundConsumer<T>(c1: Consumer<in T>, c2: Consumer<in T>) : Consumer<T> {
  var consumers: MutableList<Consumer<in T>>? = ArrayList()

  init {
    synchronized(this) {
      consumers!!.add(c1)
      consumers!!.add(c2)
    }
  }

  override fun consume(t: T) {
    val list = synchronized(this) {
      val list = consumers
      consumers = null
      list
    } ?: return

    for (consumer in list) {
      if (!isObsolete(consumer)) {
        consumer.consume(t)
      }
    }
  }

  fun add(consumer: Consumer<in T>) {
    synchronized(this) {
      consumers.let {
        if (it == null) {
          // it means that clearHandlers was called
        }
        consumers?.add(consumer)
      }
    }
  }
}

internal fun isObsolete(consumer: Consumer<*>?) = consumer is Obsolescent && consumer.isObsolete

inline fun <T> AsyncPromise<*>.catchError(runnable: () -> T): T? {
  try {
    return runnable()
  }
  catch (e: Throwable) {
    setError(e)
    return null
  }
}

private val cancelledPromise = RejectedPromise<Any?>(OBSOLETE_ERROR)

@Suppress("CAST_NEVER_SUCCEEDS")
fun <T> cancelledPromise(): Promise<T> = cancelledPromise as Promise<T>

fun <T> rejectedPromise(error: Throwable): Promise<T> = Promise.reject(error)