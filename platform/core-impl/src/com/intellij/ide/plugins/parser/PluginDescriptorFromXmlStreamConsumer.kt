// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.DataLoader
import com.intellij.ide.plugins.XIncludeLoader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginDescriptorFromXmlStreamConsumer(
  val readContext: ReadModuleContext,
  val dataLoader: DataLoader,
  val xIncludeLoader: XIncludeLoader?,
  val includeBase: String?,
  readInto: RawPluginDescriptor? = null,
) : PluginXmlStreamConsumer {
  internal val raw = readInto ?: RawPluginDescriptor()

  fun build(): RawPluginDescriptor = raw

  override fun consume(reader: XMLStreamReader2) {
    readModuleDescriptor(
      builder = this,
      reader = reader,
    )
  }
}