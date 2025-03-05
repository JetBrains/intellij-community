// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.XIncludeLoader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginDescriptorFromXmlStreamConsumer private constructor(
  val readContext: ReadModuleContext,
  val xIncludeLoader: XIncludeLoader?,
  includeBase: String?,
) : PluginXmlStreamConsumer {
  constructor(
    readContext: ReadModuleContext,
    xIncludeLoader: XIncludeLoader?,
  ) : this(readContext, xIncludeLoader, null)

  internal val raw = RawPluginDescriptor()
  private val includeBaseStack = mutableListOf<String?>()

  init {
    if (includeBase != null) {
      includeBaseStack.add(includeBase)
    }
  }

  fun build(): RawPluginDescriptor = raw

  override fun consume(reader: XMLStreamReader2) {
    readModuleDescriptor(
      builder = this,
      reader = reader,
    )
  }

  internal val includeBase: String?
    get() = includeBaseStack.lastOrNull()

  internal fun pushIncludeBase(newBase: String?) {
    includeBaseStack.add(newBase)
  }

  internal fun popIncludeBase() {
    includeBaseStack.removeLast()
  }

  companion object {
    internal fun withIncludeBase(
      readContext: ReadModuleContext,
      xIncludeLoader: XIncludeLoader?,
      includeBase: String?,
    ): PluginDescriptorFromXmlStreamConsumer = PluginDescriptorFromXmlStreamConsumer(readContext, xIncludeLoader, includeBase)
  }
}