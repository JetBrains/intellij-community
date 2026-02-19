// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.openapi.util.Disposer
import com.intellij.util.ArrayUtilRt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.BroadcastDirection
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.function.Predicate

@VisibleForTesting
@Internal
open class CompositeMessageBus : MessageBusImpl, MessageBusEx {
  private val childBuses = ContainerUtil.createLockFreeCopyOnWriteList<MessageBusImpl>()

  constructor(owner: MessageBusOwner, parentBus: CompositeMessageBus) : super(owner, parentBus)

  // root message bus constructor
  internal constructor(owner: MessageBusOwner) : super(owner)

  final override fun hasChildren(): Boolean = childBuses.isNotEmpty()

  fun addChild(bus: MessageBusImpl) {
    childrenListChanged(this)
    childBuses.add(bus)
  }

  internal fun onChildBusDisposed(childBus: MessageBusImpl) {
    val removed = childBuses.remove(childBus)
    childrenListChanged(this)
    LOG.assertTrue(removed)
  }

  final override fun <L> createPublisher(topic: Topic<L>, direction: BroadcastDirection): MessagePublisher<L> {
    return when (direction) {
      BroadcastDirection.TO_PARENT -> ToParentMessagePublisher(topic, this)
      BroadcastDirection.TO_DIRECT_CHILDREN -> {
        require(parentBus == null) {
          "Broadcast direction TO_DIRECT_CHILDREN is allowed only for app level message bus. " +
          "Please publish to app level message bus or change topic ${topic.listenerClass} broadcast direction to NONE or TO_PARENT"
        }
        ToDirectChildrenMessagePublisher(topic = topic, bus = this, childBuses = childBuses)
      }
      else -> MessagePublisher(topic, this)
    }
  }

  final override fun computeSubscribers(topic: Topic<*>): Array<Any?> {
    // light project
    return if (owner.isDisposed) ArrayUtilRt.EMPTY_OBJECT_ARRAY else super.computeSubscribers(topic)
  }

  final override fun doComputeSubscribers(topic: Topic<*>, result: MutableList<in Any>, subscribeLazyListeners: Boolean) {
    super.doComputeSubscribers(topic, result, subscribeLazyListeners)

    if (topic.broadcastDirection == BroadcastDirection.TO_CHILDREN) {
      for (childBus in childBuses) {
        if (!childBus.isDisposed) {
          childBus.doComputeSubscribers(topic, result, !childBus.owner.isParentLazyListenersIgnored)
        }
      }
    }
  }

  final override fun notifyOnSubscriptionToTopicToChildren(topic: Topic<*>) {
    for (childBus in childBuses) {
      childBus.subscriberCache.remove(topic)
      childBus.notifyOnSubscriptionToTopicToChildren(topic)
    }
  }

  final override fun notifyConnectionTerminated(topicAndHandlerPairs: Array<Any>): Boolean {
    val isChildClearingNeeded = super.notifyConnectionTerminated(topicAndHandlerPairs)
    if (!isChildClearingNeeded) {
      return false
    }

    for (bus in childBuses) {
      bus.clearSubscriberCache(topicAndHandlerPairs)
    }

    // disposed handlers are not removed for TO_CHILDREN topics in the same way as for other directions
    // because it is not wise to check each child bus
    rootBus.queue.queue.removeIf { nullizeHandlersFromMessage(it, topicAndHandlerPairs) }
    return false
  }

  final override fun clearSubscriberCache(topicAndHandlerPairs: Array<Any>) {
    super.clearSubscriberCache(topicAndHandlerPairs)

    for (bus in childBuses) {
      bus.clearSubscriberCache(topicAndHandlerPairs)
    }
  }

  final override fun removeEmptyConnectionsRecursively() {
    super.removeEmptyConnectionsRecursively()

    for (bus in childBuses) {
      bus.removeEmptyConnectionsRecursively()
    }
  }

  /**
   * Clear publisher cache, including child buses.
   */
  final override fun clearPublisherCache() {
    // keep it simple - we can infer plugin id from topic.getListenerClass(), but granular clearing is not worth the code complication
    super.clearPublisherCache()
    for (childBus in childBuses) {
      if (childBus is CompositeMessageBus) {
        childBus.clearPublisherCache()
      }
      else {
        childBus.publisherCache.clear()
      }
    }
  }

  final override fun disconnectPluginConnections(predicate: Predicate<Class<*>>) {
    super.disconnectPluginConnections(predicate)

    for (bus in childBuses) {
      bus.disconnectPluginConnections(predicate)
    }
  }

  @TestOnly
  final override fun clearAllSubscriberCache() {
    super.clearAllSubscriberCache()
    for (bus in childBuses) {
      bus.subscriberCache.clear()
    }
  }

  final override fun disposeChildren() {
    for (childBus in childBuses) {
      Disposer.dispose(childBus)
    }
  }
}

private class ToDirectChildrenMessagePublisher<L>(topic: Topic<L>, bus: CompositeMessageBus, private val childBuses: List<MessageBusImpl>)
  : MessagePublisher<L>(topic, bus), InvocationHandler {
  override fun publish(method: Method, args: Array<Any?>?, queue: MessageQueue?): Boolean {
    var error: Throwable? = null
    var hasHandlers = false
    var handlers = bus.subscriberCache.computeIfAbsent(topic, bus::computeSubscribers)
    if (handlers.isNotEmpty()) {
      error = executeOrAddToQueue(
        topic = topic,
        method = method,
        args = args,
        handlers = handlers,
        jobQueue = queue,
        prevError = null,
        bus = bus,
      )
      hasHandlers = true
    }

    for (childBus in childBuses) {
      // light project in tests is not disposed correctly
      if (childBus.owner.isDisposed) {
        continue
      }

      handlers = childBus.subscriberCache.computeIfAbsent(topic) { topic1 ->
        val result = mutableListOf<Any>()
        childBus.doComputeSubscribers(
          topic = topic1,
          result = result,
          subscribeLazyListeners = !childBus.owner.isParentLazyListenersIgnored,
        )
        if (result.isEmpty()) {
          ArrayUtilRt.EMPTY_OBJECT_ARRAY
        }
        else {
          result.toTypedArray()
        }
      }
      if (handlers.isEmpty()) {
        continue
      }

      hasHandlers = true
      error = executeOrAddToQueue(
        topic = topic,
        method = method,
        args = args,
        handlers = handlers,
        jobQueue = queue,
        prevError = error,
        bus = childBus,
      )
    }
    error?.let(::throwError)
    return hasHandlers
  }
}

private fun childrenListChanged(changedBus: MessageBusImpl) {
  var bus = changedBus
  while (true) {
    bus.subscriberCache.keys.removeIf { it.broadcastDirection == BroadcastDirection.TO_CHILDREN }
    bus = bus.parentBus ?: break
  }
}

/**
 * Returns true if no more handlers.
 */
private fun nullizeHandlersFromMessage(message: Message, topicAndHandlerPairs: Array<Any>): Boolean {
  var nullElementCount = 0
  for (messageIndex in message.handlers.indices) {
    val handler = message.handlers[messageIndex]
    if (handler == null) {
      nullElementCount++
    }

    var i = 0
    while (i < topicAndHandlerPairs.size) {
      if (message.topic === topicAndHandlerPairs[i] && handler === topicAndHandlerPairs[i + 1]) {
        message.handlers[messageIndex] = null
        nullElementCount++
      }
      i += 2
    }
  }
  return nullElementCount == message.handlers.size
}