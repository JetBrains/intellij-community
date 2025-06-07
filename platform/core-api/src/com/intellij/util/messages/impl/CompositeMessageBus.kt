// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.util.ArrayUtilRt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.BroadcastDirection
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentMap
import java.util.function.BiConsumer
import java.util.function.Predicate

private val EMPTY_MAP = HashMap<String, MutableList<PluginListenerDescriptor>>()

@Suppress("ReplaceGetOrSet")
@VisibleForTesting
@Internal
open class CompositeMessageBus : MessageBusImpl, MessageBusEx {
  private val childBuses = ContainerUtil.createLockFreeCopyOnWriteList<MessageBusImpl>()

  @Volatile
  private var topicClassToListenerDescriptor: MutableMap<String, MutableList<PluginListenerDescriptor>> = EMPTY_MAP

  constructor(owner: MessageBusOwner, parentBus: CompositeMessageBus) : super(owner, parentBus)

  // root message bus constructor
  internal constructor(owner: MessageBusOwner) : super(owner)

  /**
   * Must be a concurrent map, because remove operation may be concurrently performed (synchronized only per topic).
   */
  final override fun setLazyListeners(map: ConcurrentMap<String, MutableList<PluginListenerDescriptor>>) {
    val topicClassToListenerDescriptor = topicClassToListenerDescriptor
    if (topicClassToListenerDescriptor === EMPTY_MAP) {
      this.topicClassToListenerDescriptor = map
    }
    else {
      topicClassToListenerDescriptor.putAll(map)
      // adding project level listener to an app level topic is not recommended, but supported
      if (rootBus !== this) {
        rootBus.subscriberCache.clear()
      }
      subscriberCache.clear()
    }
  }

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
    if (subscribeLazyListeners) {
      subscribeLazyListeners(topic)
    }

    super.doComputeSubscribers(topic, result, subscribeLazyListeners)

    if (topic.broadcastDirection == BroadcastDirection.TO_CHILDREN) {
      for (childBus in childBuses) {
        if (!childBus.isDisposed) {
          childBus.doComputeSubscribers(topic, result, !childBus.owner.isParentLazyListenersIgnored)
        }
      }
    }
  }

  private fun <L> subscribeLazyListeners(topic: Topic<L>) {
    if (topic.listenerClass === Runnable::class.java) {
      return
    }

    val topicClassToListenerDescriptor = topicClassToListenerDescriptor
    if (topicClassToListenerDescriptor === EMPTY_MAP) {
      return
    }

    Cancellation.computeInNonCancelableSection<Unit, Exception> {
      // use a linked hash map for repeatable results
      val listenerDescriptors = topicClassToListenerDescriptor.remove(topic.listenerClass.name) ?: return@computeInNonCancelableSection
      val listenerMap = LinkedHashMap<PluginDescriptor, MutableList<Any>>()
      for (listenerDescriptor in listenerDescriptors) {
        try {
          listenerMap.computeIfAbsent(listenerDescriptor.pluginDescriptor) { mutableListOf() }.add(owner.createListener(listenerDescriptor))
        }
        catch (_: ExtensionNotApplicableException) {
        }
        catch (e: ProcessCanceledException) {
          // ProgressManager have an asserting for this case
          throw e
        }
        catch (e: Throwable) {
          LOG.error("Cannot create listener", e)
        }
      }
      listenerMap.forEach(BiConsumer { key, listeners ->
        subscribers.add(DescriptorBasedMessageBusConnection(key, topic, listeners))
      })
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
    publisherCache.clear()
    for (childBus in childBuses) {
      if (childBus is CompositeMessageBus) {
        childBus.clearPublisherCache()
      }
      else {
        childBus.publisherCache.clear()
      }
    }
  }

  final override fun unsubscribeLazyListeners(module: IdeaPluginDescriptor, listenerDescriptors: List<ListenerDescriptor>) {
    topicClassToListenerDescriptor.values.removeIf(Predicate { descriptors ->
      if (descriptors.removeIf { it.pluginDescriptor === module }) {
        return@Predicate descriptors.isEmpty()
      }
      false
    })
    if (listenerDescriptors.isEmpty() || subscribers.isEmpty()) {
      return
    }

    val topicToDescriptors = HashMap<String, MutableSet<String>>()
    for (descriptor in listenerDescriptors) {
      topicToDescriptors.computeIfAbsent(descriptor.topicClassName) { HashSet() }.add(descriptor.listenerClassName)
    }

    var isChanged = false
    var newSubscribers: MutableList<DescriptorBasedMessageBusConnection?>? = null
    val connectionIterator = subscribers.iterator()
    while (connectionIterator.hasNext()) {
      val connection = connectionIterator.next() as? DescriptorBasedMessageBusConnection ?: continue
      if (module !== connection.module) {
        continue
      }

      val listenerClassNames = topicToDescriptors.get(connection.topic.listenerClass.name) ?: continue
      val newHandlers = computeNewHandlers(connection.handlers, listenerClassNames) ?: continue
      isChanged = true
      connectionIterator.remove()
      if (newHandlers.isNotEmpty()) {
        if (newSubscribers == null) {
          newSubscribers = mutableListOf()
        }
        newSubscribers.add(DescriptorBasedMessageBusConnection(module, connection.topic, newHandlers))
      }
    }

    // todo it means that order of subscribers is not preserved
    // it is a very minor requirement, but still, makes sense to comply it
    if (newSubscribers != null) {
      subscribers.addAll(newSubscribers)
    }
    if (isChanged) {
      // we can check it more precisely, but for simplicity, just clearing all
      // adding a project level listener for an app level topic is not recommended, but supported
      if (rootBus !== this) {
        rootBus.subscriberCache.clear()
      }
      subscriberCache.clear()
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
    LOG.assertTrue(rootBus !== this)

    rootBus.subscriberCache.clear()
    subscriberCache.clear()
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

private fun computeNewHandlers(handlers: List<Any>, excludeClassNames: Set<String?>): List<Any>? {
  var newHandlers: MutableList<Any>? = null
  var i = 0
  val size = handlers.size
  while (i < size) {
    val handler = handlers[i]
    if (excludeClassNames.contains(handler::class.java.name)) {
      if (newHandlers == null) {
        newHandlers = if (i == 0) mutableListOf() else handlers.subList(0, i).toMutableList()
      }
    }
    else {
      newHandlers?.add(handler)
    }
    i++
  }
  return newHandlers
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