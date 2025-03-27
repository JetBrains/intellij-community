// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.messages.ListenerDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginListenerDescriptor(val descriptor: ListenerDescriptor, val pluginDescriptor: PluginDescriptor)

@get:ApiStatus.Internal
val PluginListenerDescriptor.listenerClassName: String get() = descriptor.listenerClassName