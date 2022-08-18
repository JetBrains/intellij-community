// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient.response

import java.net.http.HttpResponse.*
import java.util.concurrent.Flow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class CancellableWrappingBodyHandler<T>(
  private val handler: BodyHandler<T>
) : BodyHandler<T> by handler {

  @Volatile
  private var cancelled = false

  @Volatile
  private var currentSubscription: Flow.Subscription? = null

  private val lock = ReentrantLock()

  override fun apply(responseInfo: ResponseInfo): BodySubscriber<T> =
    SubscriberWrapper(handler.apply(responseInfo))

  fun cancel() {
    lock.withLock {
      cancelled = true
      currentSubscription?.cancel()
    }
  }

  private inner class SubscriberWrapper<O>(private val subscriber: BodySubscriber<O>) : BodySubscriber<O> by subscriber {
    override fun onSubscribe(subscription: Flow.Subscription) {
      subscriber.onSubscribe(subscription)
      lock.withLock {
        if (cancelled) subscription.cancel()
        currentSubscription = subscription
      }
    }
  }
}