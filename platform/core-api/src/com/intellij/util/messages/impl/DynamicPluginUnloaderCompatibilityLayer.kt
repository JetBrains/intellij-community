// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.ide.plugins.CannotUnloadPluginException
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic.BroadcastDirection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
object DynamicPluginUnloaderCompatibilityLayer {
  /**
   * Fallback implementation to receive exceptions from a message bus listener.
   * The [BroadcastDirection] is not being honored
   *
   * Use [com.intellij.ide.plugins.DynamicPluginVetoer] instead.
   */
  fun queryPluginUnloadVetoers(pluginDescriptor: IdeaPluginDescriptor, messageBus: MessageBus): @Nls String? {
    try {
      if (messageBus !is MessageBusImpl) return null

      val subscribers = messageBus.computeSubscribers(DynamicPluginListener.TOPIC)
      for (subscriber in subscribers) {
        if (subscriber is DynamicPluginListener) {
          subscriber.checkUnloadPlugin(pluginDescriptor)
        }
      }

      return null
    }
    catch (e: CannotUnloadPluginException) {
      return e.cause?.localizedMessage ?: "checkUnloadPlugin listener blocked plugin unload"
    }
    catch (_: kotlinx.coroutines.CancellationException) {
      return null
    }
    catch (e: Throwable) {
      Logger.getInstance(DynamicPluginListener::class.java).error(e)
      return null
    }
  }
}