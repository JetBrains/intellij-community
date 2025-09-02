// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentMap
import java.util.function.Predicate

@ApiStatus.Internal
interface MessageBusEx : MessageBus {
  fun clearPublisherCache()

  fun unsubscribeLazyListeners(module: IdeaPluginDescriptor, listenerDescriptors: List<ListenerDescriptor>)

  /**
   * Must be called only on a root bus.
   */
  fun disconnectPluginConnections(predicate: Predicate<Class<*>>)

  @TestOnly
  fun clearAllSubscriberCache()

  fun setLazyListeners(map: ConcurrentMap<String, MutableList<PluginListenerDescriptor>>)
}