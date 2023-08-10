// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PathBasedJdomXIncluder")

package com.intellij.ide.plugins

interface PathResolver {
  val isFlat: Boolean
    get() = false

  fun loadXIncludeReference(readInto: RawPluginDescriptor,
                            readContext: ReadModuleContext,
                            dataLoader: DataLoader,
                            base: String?,
                            relativePath: String): Boolean

  fun resolvePath(readContext: ReadModuleContext,
                  dataLoader: DataLoader,
                  relativePath: String,
                  readInto: RawPluginDescriptor?): RawPluginDescriptor?

  // module in a new file name format must be always resolved
  fun resolveModuleFile(readContext: ReadModuleContext,
                        dataLoader: DataLoader,
                        path: String,
                        readInto: RawPluginDescriptor?): RawPluginDescriptor
}