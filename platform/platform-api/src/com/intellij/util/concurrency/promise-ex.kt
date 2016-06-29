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
@file:JvmName("Promises")
package org.jetbrains.concurrency

import com.intellij.util.Consumer

fun resolvedPromise(): Promise<*> = Promise.DONE

fun <T> resolvedPromise(result: T) = Promise.resolve(result)

fun all(promises: Collection<Promise<*>>) = if (promises.size == 1) promises.first() else all(promises, null)

fun <T> all(promises: Collection<Promise<*>>, totalResult: T?): Promise<T> {
  if (promises.isEmpty()) {
    return resolvedPromise(null)
  }

  val totalPromise = AsyncPromise<T>()
  val done = CountDownConsumer(promises.size, totalPromise, totalResult)
  val rejected = Consumer<Throwable> { error -> totalPromise.setError(error) }

  for (promise in promises) {
    promise.done(done)
    promise.rejected(rejected)
  }
  return totalPromise
}

private class CountDownConsumer<T>(@Volatile private var countDown: Int, private val promise: AsyncPromise<T>, private val totalResult: T?) : Consumer<Any?> {
  override fun consume(t: Any?) {
    if (--countDown == 0) {
      promise.setResult(totalResult)
    }
  }
}

fun <T> any(promises: Collection<Promise<T>>, totalError: String): Promise<T> {
  if (promises.isEmpty()) {
    return resolvedPromise(null)
  }
  else if (promises.size == 1) {
    return promises.first()
  }

  val totalPromise = AsyncPromise<T>()
  val done = Consumer<T> { result -> totalPromise.setResult(result) }
  val rejected = object : Consumer<Throwable> {
    @Volatile private var toConsume = promises.size

    override fun consume(throwable: Throwable) {
      if (--toConsume <= 0) {
        totalPromise.setError(totalError)
      }
    }
  }

  for (promise in promises) {
    promise.done(done)
    promise.rejected(rejected)
  }
  return totalPromise
}