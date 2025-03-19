// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

interface ExtensionPointListener<T> {

  fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {}

  fun extensionRemoved(extension: T, pluginDescriptor: PluginDescriptor) {}
}