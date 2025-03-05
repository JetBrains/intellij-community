// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.parser.RawPluginDescriptor
import com.intellij.ide.plugins.parser.ReadModuleContext

interface XIncludeLoader {
  fun loadXIncludeReference(readInto: RawPluginDescriptor, readContext: ReadModuleContext, dataLoader: DataLoader, base: String?, relativePath: String): Boolean
}