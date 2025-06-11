// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.MessageBusImpl.Companion.LOG
import com.intellij.util.messages.impl.MessageBusImpl.MessageHandlerHolder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiConsumer
import java.util.function.Predicate

internal fun subscribeLazyListeners(
  topic: Topic<*>,
  topicClassToListenerDescriptor: MutableMap<String, MutableList<PluginListenerDescriptor>>,
  subscribers: ConcurrentLinkedQueue<MessageHandlerHolder>,
  owner: MessageBusOwner,
) {
  Cancellation.withNonCancelableSection().use {
    // use a linked hash map for repeatable results
    val listenerDescriptors = topicClassToListenerDescriptor.remove(topic.listenerClass.name) ?: return@use
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

internal fun unsubscribeLazyListeners(
  module: IdeaPluginDescriptor,
  listenerDescriptors: List<ListenerDescriptor>,
  topicClassToListenerDescriptor: MutableMap<String, MutableList<PluginListenerDescriptor>>,
  subscribers: ConcurrentLinkedQueue<MessageHandlerHolder>,
): Boolean {
  topicClassToListenerDescriptor.values.removeIf(Predicate { descriptors ->
    if (descriptors.removeIf { it.pluginDescriptor === module }) {
      return@Predicate descriptors.isEmpty()
    }
    false
  })
  if (listenerDescriptors.isEmpty() || subscribers.isEmpty()) {
    return false
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
  return isChanged
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