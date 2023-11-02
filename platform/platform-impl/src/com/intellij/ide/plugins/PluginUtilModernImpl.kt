// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.extensions.PluginId

private class PluginUtilModernImpl : PluginUtilImpl() {
  private val walker = StackWalker.getInstance(setOf(StackWalker.Option.RETAIN_CLASS_REFERENCE), 5)

  override fun getCallerPlugin(stackFrameCount: Int): PluginId? {
    val aClass = walker.walk { stream -> stream.skip((stackFrameCount).toLong()).map { it.declaringClass }.findFirst().orElse(null) }
    return (aClass?.classLoader as? PluginAwareClassLoader)?.pluginId
  }
}