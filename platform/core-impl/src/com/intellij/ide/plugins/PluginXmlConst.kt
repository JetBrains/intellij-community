// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginXmlConst {
  const val PLUGIN_PACKAGE_ATTR: String = "package"
  const val PLUGIN_IMPLEMENTATION_DETAIL_ATTR: String = "implementation-detail"

  const val DEFAULT_XPOINTER_VALUE: String = "xpointer(/idea-plugin/*)"
}