// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.ArrayUtilRt
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.MessageBusImpl.MessageHandlerHolder
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate

internal sealed class BaseBusConnection(bus: MessageBusImpl) : MessageHandlerHolder {
  @JvmField
  var bus: MessageBusImpl? = bus

  // array of topic1: Topic<L>, handler1: L, topic2: Topic<L>, handler2: L, ...
  @JvmField
  protected val subscriptions = AtomicReference(ArrayUtilRt.EMPTY_OBJECT_ARRAY)

  override val isDisposed: Boolean
    get() = bus == null

  fun <L : Any> subscribe(topic: Topic<L>, handler: L) {
    val liveBus = bus ?: throw AlreadyDisposedException("Message bus connection is closed: $this")
    var list: Array<Any>
    var newList: Array<Any>
    do {
      list = subscriptions.get()
      if (list.isEmpty()) {
        newList = arrayOf(topic, handler)
      }
      else {
        val size = list.size
        @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
        newList = Arrays.copyOf(list, size + 2)
        newList[size] = topic
        newList[size + 1] = handler
      }
    }
    while (!subscriptions.compareAndSet(list, newList))
    liveBus.notifyOnSubscription(topic)
  }

  override fun collectHandlers(topic: Topic<*>, result: MutableList<in Any>) {
    val list = subscriptions.get()
    var i = 0
    val n = list.size
    while (i < n) {
      if (list[i] === topic) {
        result.add(list[i + 1])
      }
      i += 2
    }
  }

  override fun disconnectIfNeeded(predicate: Predicate<Class<*>>) {
    while (true) {
      val list = subscriptions.get()
      var newList: MutableList<Any>? = null
      var i = 0
      while (i < list.size) {
        if (predicate.test(list[i + 1].javaClass)) {
          if (newList == null) {
            newList = list.asList().subList(0, i).toMutableList()
          }
        }
        else if (newList != null) {
          newList.add(list[i])
          newList.add(list[i + 1])
        }
        i += 2
      }

      if (newList == null) {
        return
      }

      if (newList.isEmpty()) {
        disconnect()
        return
      }
      else if (subscriptions.compareAndSet(list, newList.toTypedArray())) {
        break
      }
    }
  }

  protected abstract fun disconnect()

  override fun toString(): String = subscriptions.get().contentToString()
}