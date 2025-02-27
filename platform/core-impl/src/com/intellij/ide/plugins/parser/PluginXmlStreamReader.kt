// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.DataLoader
import com.intellij.ide.plugins.PathResolver
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginXmlStreamReader(
  val readContext: ReadModuleContext,
  val dataLoader: DataLoader,
  val pathResolver: PathResolver,
  val includeBase: String?,
  readInto: RawPluginDescriptor? = null,
) : PluginXmlStreamConsumer {
  internal val builder = readInto ?: RawPluginDescriptor()

  fun getRawPluginDescriptor(): RawPluginDescriptor = builder

  override fun consume(reader: XMLStreamReader2) {
    readModuleDescriptor(
      reader = reader,
      readContext = readContext,
      dataLoader = dataLoader,
      pathResolver = pathResolver,
      includeBase = includeBase,
      readInto = builder
    )
  }
}