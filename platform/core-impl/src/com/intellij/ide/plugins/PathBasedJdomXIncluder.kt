// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PathBasedJdomXIncluder")

package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PathResolver {
  val isFlat: Boolean
    get() = false

  fun loadXIncludeReference(readInto: RawPluginDescriptor, readContext: ReadModuleContext, dataLoader: DataLoader, base: String?, relativePath: String): Boolean

  fun resolvePath(readContext: ReadModuleContext, dataLoader: DataLoader, relativePath: String, readInto: RawPluginDescriptor?): RawPluginDescriptor?

  // module in a new file name format must always be resolved
  fun resolveModuleFile(readContext: ReadModuleContext, dataLoader: DataLoader, path: String, readInto: RawPluginDescriptor?): RawPluginDescriptor
}